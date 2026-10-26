package com.pox.kernelmanager.data.repo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.CpuCoreStat
import com.pox.kernelmanager.data.model.DeviceInfo
import com.pox.kernelmanager.data.model.HardwareStats
import com.pox.kernelmanager.data.root.KernelNodes
import com.pox.kernelmanager.data.root.PoxConfigBridge
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object HardwareRepository {

    suspend fun getDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        val procVersion = ShellUtils.fastRead("/proc/version") ?: System.getProperty("os.version") ?: ""
        val utsRelease = ShellUtils.fastRead("/proc/sys/kernel/osrelease") ?: ""

        val isPox = utsRelease.contains("Pox", ignoreCase = true) || procVersion.contains("Pox", ignoreCase = true)

        // Pox kernels expose variant/commit separately because UTS_RELEASE is
        // intentionally variant-free (always 4.14.357-Pox). Older builds did
        // encode the edition and commit in utsRelease, so keep that as a
        // fallback for devices that have not updated yet.
        val poxBuild = ShellUtils.fastRead(KernelNodes.POX_BUILD_PROC)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator > 0) {
                    line.substring(0, separator) to line.substring(separator + 1).trim()
                } else {
                    null
                }
            }
            ?.toMap()
            .orEmpty()

        val legacyEdition = when {
            utsRelease.contains("Onyx-Sultan-APatch", true) -> "Onyx-Sultan-APatch"
            utsRelease.contains("Onyx-Sultan", true) -> "Onyx-Sultan"
            utsRelease.contains("Onyx-KSU-Next", true) -> "Onyx-KSU-Next"
            utsRelease.contains("Onyx-APatch", true) -> "Onyx-APatch"
            utsRelease.contains("Onyx", true) -> "Onyx"
            utsRelease.contains("Granite", true) -> "Granite"
            utsRelease.contains("Obsidian", true) -> "Obsidian"
            else -> if (isPox) "Pox Custom" else "Stock / Other"
        }

        val commitRegex = Regex("([a-f0-9]{7,12})")
        val edition = poxBuild["variant"].orEmpty().ifBlank { legacyEdition }
        val commit = poxBuild["commit"].orEmpty().ifBlank {
            commitRegex.find(utsRelease)?.value ?: ""
        }

        // One probe for the whole root state: provider, version and grant status come
// from the same snapshot instead of three independent round-trips.
        val rootEnv = ShellUtils.environment()
        val selinux = ShellUtils.detectSELinux()
        val noMount = ShellUtils.detectNoMount()

        DeviceInfo(
            deviceModel = android.os.Build.MODEL ?: "Redmi Note 8 Pro",
            codename = android.os.Build.DEVICE ?: "begonia",
            socName = "MediaTek MT6785 (Helio G90T)",
            kernelRelease = utsRelease.ifEmpty { procVersion.take(40) },
            linuxVersion = "Linux 4.14.357",
            kernelEdition = edition,
            commitHash = commit,
            isPoxKernel = isPox,
            rootProvider = rootEnv.provider,
            rootProviderVersion = rootEnv.version,
            rootGranted = rootEnv.granted,
            selinuxStatus = selinux,
            noMountActive = noMount
        )
    }

    suspend fun getHardwareStats(): HardwareStats = withContext(Dispatchers.IO) {
        // Collect telemetry using zero-overhead direct File reads; only unreadable nodes fallback to shell
        val statNodes = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu2/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu3/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu5/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
            "/sys/kernel/ged/hal/current_freqency",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/proc/meminfo",
            "/sys/block/zram0/mm_stat",
            "/sys/block/zram0/disksize"
        )
        val batch = HashMap<String, String>()
        val unreadable = mutableListOf<String>()

        for (node in statNodes) {
            val content = readDirect(node)
            if (content != null) {
                batch[node] = content
            } else {
                unreadable.add(node)
            }
        }

        if (unreadable.isNotEmpty() && ShellUtils.isRootAvailable()) {
            val shellBatch = ShellUtils.batchRead(unreadable)
            batch.putAll(shellBatch)
        }

        // 1. CPU Cores stats (Helio G90T Octa-Core: 0-5 Little A55, 6-7 Big A76)
        val cores = mutableListOf<CpuCoreStat>()
        for (i in 0 until 8) {
            val freqPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val curFreq = (batch[freqPath]?.toIntOrNull() ?: (if (i >= 6) 1085000 else 1400000)) / 1000
            val minFreq = if (i >= 6) 800 else 400
            val maxFreq = if (i >= 6) 2050 else 2000

            cores.add(
                CpuCoreStat(
                    coreId = i,
                    currentFreqMhz = curFreq,
                    minFreqMhz = minFreq,
                    maxFreqMhz = maxFreq,
                    isBigCore = (i >= 6)
                )
            )
        }

        val governor = batch["/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"] ?: "schedutil"

        // 2. GPU Frequency & Load (MediaTek Mali-G76 MC4 via GED HAL)
        val gpuFreqKhz = batch["/sys/kernel/ged/hal/current_freqency"]
            ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
        val gpuFreq = if (gpuFreqKhz != null) {
            (gpuFreqKhz / 1000).toInt()
        } else {
            800
        }

        val gpuLoadVal = batch["/sys/kernel/ged/hal/gpu_utilization"]
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull()
        val gpuLoad = gpuLoadVal?.coerceIn(0, 100) ?: 12

        // 3. RAM & ZRAM (from /proc/meminfo)
        val meminfo = batch["/proc/meminfo"] ?: ""
        var ramTotal = 0L
        var ramFree = 0L
        var ramAvailable = 0L

        meminfo.lines().forEach { line ->
            when {
                line.startsWith("MemTotal:") -> ramTotal = parseKb(line)
                line.startsWith("MemFree:") -> ramFree = parseKb(line)
                line.startsWith("MemAvailable:") -> ramAvailable = parseKb(line)
            }
        }
        val ramUsed = if (ramAvailable > 0) ramTotal - ramAvailable else ramTotal - ramFree

        val mmStat = batch["/sys/block/zram0/mm_stat"]
            ?.trim()
            ?.split(Regex("\\s+"))
            .orEmpty()

        val zramDisksize = (batch["/sys/block/zram0/disksize"]?.toLongOrNull() ?: 0L) / (1024 * 1024)
        val zramOrig = mmStat.getOrNull(0)?.toLongOrNull() ?: 0L
        val zramUsed = mmStat.getOrNull(2)?.toLongOrNull() ?: 0L
        val zramRatio = if (zramUsed > 0) zramOrig.toFloat() / zramUsed.toFloat() else 1.0f
        val zramOrigMb = zramOrig / (1024 * 1024)
        val zramUsedMb = zramUsed / (1024 * 1024)

        // 4. Battery & Power Telemetry (Accurate Android BatteryManager + Intent + sysfs)
        var batCapacity = 78
        var batVoltageMv = 4148
        var batTempC = 25.0f
        var batStatus = "Charging (USB)"
        var batCurrentMa = 450

        // Single config query from kernel to eliminate polling lag
        val poxConfig = PoxConfigBridge.readConfig()

        try {
            val context = PoxApplication.instance
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bIntent = context.registerReceiver(null, ifilter)

            // Real Battery Capacity
            val capacityFromBm = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val levelFromIntent = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100

            batCapacity = when {
                capacityFromBm in 1..100 -> capacityFromBm
                levelFromIntent >= 0 && scale > 0 -> (levelFromIntent * 100) / scale
                else -> readDirect("/sys/class/power_supply/battery/capacity")?.toIntOrNull()
                    ?: ShellUtils.fastRead("/sys/class/power_supply/battery/capacity")?.toIntOrNull() ?: 78
            }

            // Real Battery Voltage
            val vExtra = bIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            batVoltageMv = if (vExtra > 0) vExtra else {
                val rawV = readDirect("/sys/class/power_supply/battery/voltage_now")?.toIntOrNull()
                    ?: ShellUtils.fastRead("/sys/class/power_supply/battery/voltage_now")?.toIntOrNull() ?: 4148000
                if (rawV > 100000) rawV / 1000 else rawV
            }

            // Real Temperature
            val tExtra = bIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            batTempC = if (tExtra > 0) (tExtra / 10.0f) else {
                val rawT = readDirect("/sys/class/power_supply/battery/temp")?.toIntOrNull()
                    ?: ShellUtils.fastRead("/sys/class/power_supply/battery/temp")?.toIntOrNull() ?: 248
                rawT / 10.0f
            }

            // Real Charging Status & Source
            val statusInt = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pluggedInt = bIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                    statusInt == BatteryManager.BATTERY_STATUS_FULL

            // Health & Technology
            val healthInt = bIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
            val batHealth = when (healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                else -> readDirect("/sys/class/power_supply/battery/health")
                    ?: ShellUtils.fastRead("/sys/class/power_supply/battery/health") ?: "Good"
            }

            val batTech = bIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                ?: readDirect("/sys/class/power_supply/battery/technology")
                ?: ShellUtils.fastRead("/sys/class/power_supply/battery/technology")
                ?: "Li-poly"

            val fastChargeNode = poxConfig["fast_charge"]
                ?: readDirect(KernelNodes.FAST_CHARGE_PROC)
                ?: ShellUtils.fastRead(KernelNodes.FAST_CHARGE_PROC)
            val isFastCharge = fastChargeNode == "1" || (pluggedInt == BatteryManager.BATTERY_PLUGGED_AC && fastChargeNode != "0")

            val chargerSource = when {
                pluggedInt == BatteryManager.BATTERY_PLUGGED_AC -> if (isFastCharge) "AC Fast Charge (18W)" else "AC Standard (10W)"
                pluggedInt == BatteryManager.BATTERY_PLUGGED_USB -> "USB 2.0 (500mA SDP)"
                pluggedInt == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging"
                isCharging -> "Charger Connected"
                else -> "Battery (Discharging)"
            }

            batStatus = when {
                statusInt == BatteryManager.BATTERY_STATUS_FULL -> "Full (100%)"
                isCharging -> chargerSource
                else -> "Discharging"
            }

            // Current & Wattage
            val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val sysfsCurrent = readDirect("/sys/class/power_supply/battery/current_now")?.toIntOrNull()
                ?: ShellUtils.fastRead("/sys/class/power_supply/battery/current_now")?.toIntOrNull() ?: 0
            val effectiveUa = if (currentUa != 0 && abs(currentUa) < 10000000) currentUa else sysfsCurrent
            val rawMa = if (effectiveUa != 0 && abs(effectiveUa) < 10000000) {
                effectiveUa / 1000
            } else {
                if (isCharging) (if (isFastCharge) 2400 else 500) else -350
            }
            // Normalize sign: positive when charging, negative when discharging
            batCurrentMa = if (isCharging) abs(rawMa) else -abs(rawMa)
        } catch (_: Exception) { }

        val wattage = (batVoltageMv * abs(batCurrentMa)) / 1000000f
        val bypassMode = (poxConfig["battery_bypass"] == "1") ||
            (readDirect(KernelNodes.BATTERY_BYPASS_PROC) == "1") ||
            (readDirect(KernelNodes.BATTERY_BYPASS_SYS) == "1")

        val kernelBatStatus = readDirect(KernelNodes.BATTERY_STATUS_PROC)
            ?: readDirect(KernelNodes.BATTERY_STATUS_SYS)
            ?: (if (bypassMode) "BYPASS" else null)
        val finalBatStatus = if (!kernelBatStatus.isNullOrBlank()) {
            when {
                kernelBatStatus.contains("BYPASS", true) -> "Direct Bypass (Idle on Charger)"
                kernelBatStatus.contains("LIMIT", true) -> "Charge Limit Cap Reached"
                kernelBatStatus.contains("THERMAL", true) -> "Thermal Guard Active"
                else -> batStatus
            }
        } else batStatus

        val chargeLimitCap = poxConfig["battery_limit"]?.toIntOrNull()
            ?: readDirect(KernelNodes.BATTERY_LIMIT_PROC)?.toIntOrNull()
            ?: readDirect(KernelNodes.BATTERY_LIMIT_SYS)?.toIntOrNull()
            ?: 100

        val designCapacity = (readDirect("/sys/class/power_supply/battery/charge_full_design")?.toIntOrNull()
            ?: 4500000) / 1000
        val estimatedCapacity = (readDirect("/sys/class/power_supply/battery/charge_full")?.toIntOrNull()
            ?: 4385000) / 1000

        HardwareStats(
            cpuCores = cores,
            cpuGovernor = governor,
            gpuFreqMhz = gpuFreq,
            gpuLoadPercent = gpuLoad,
            ramTotalMb = ramTotal / 1024,
            ramUsedMb = ramUsed / 1024,
            ramFreeMb = ramAvailable / 1024,
            zramTotalMb = zramDisksize,
            zramUsedMb = zramUsedMb,
            zramCompRatio = zramRatio,
            batteryLevel = batCapacity,
            batteryVoltageMv = batVoltageMv,
            batteryCurrentMa = batCurrentMa,
            batteryWattageW = wattage,
            batteryTempC = batTempC,
            batteryStatus = finalBatStatus,
            isBypassActive = bypassMode,
            chargerSource = if (batStatus.contains("Discharging")) "Battery" else batStatus,
            isCharging = batCurrentMa > 0 || batStatus.contains("Charging", true) || batStatus.contains("Full", true),
            isFastCharging = batStatus.contains("Fast", true),
            batteryHealth = "Good",
            batteryTechnology = "Li-poly",
            chargeLimitPercent = chargeLimitCap,
            designCapacityMah = designCapacity,
            estimatedCapacityMah = estimatedCapacity,
            chargeRateMaPerHr = abs(batCurrentMa)
        )
    }

    private fun parseKb(line: String): Long {
        val parts = line.split("\\s+".toRegex())
        return parts.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun readDirect(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
