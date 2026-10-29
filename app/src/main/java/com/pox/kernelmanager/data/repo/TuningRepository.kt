package com.pox.kernelmanager.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.KprofilesLevel
import com.pox.kernelmanager.data.model.HealthCalibrationMode
import com.pox.kernelmanager.data.model.BrainlessProfile
import com.pox.kernelmanager.data.model.BrainlessPresets
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.model.EasyPresetConfig
import com.pox.kernelmanager.data.model.EasyModePresets
import com.pox.kernelmanager.data.model.TuningFeature
import com.pox.kernelmanager.data.model.TuningFeatures
import com.pox.kernelmanager.data.learning.AdaptiveLearningManager
import com.pox.kernelmanager.data.root.KernelNodes
import com.pox.kernelmanager.data.root.PoxConfigBridge
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TuningApplyResult(
    val isHardwareApplied: Boolean,
    val message: String
)

object TuningRepository {

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences("pox_tuning_prefs", Context.MODE_PRIVATE)
    }

    /**
     * EAS boost applied per gaming level.
     *
     * `eas_ctrl.c` clamps through `MIN_BOOST_VALUE`/`MAX_BOOST_VALUE`, both -100
     * and 100, so these stay inside the range the kernel accepts.
     */
    private val boostForMode = mapOf(
        GamingModeLevel.POWERSAVE to -20,
        GamingModeLevel.BALANCED to 0,
        GamingModeLevel.GAMING to 50,
        GamingModeLevel.EXTREME to 100
    )

    /**
     * Write a set of kernel nodes and report what actually happened.
     *
     * [writes] is a list of path/value pairs; the call succeeds when at least one
     * of them lands, because several of these features are reachable through more
     * than one node.
     *
     * Every setter used to ignore `fastWrite` and return success unconditionally,
     * so a missing `/proc/perfmgr` node was indistinguishable from a working
     * write. Nothing here targets a node this kernel does not build.
     */
    private val pathToConfigKey: Map<String, String> = mapOf(
        KernelNodes.GAMING_MODE_PROC to "gaming_mode",
        KernelNodes.GAMING_MODE_SYS to "gaming_mode",
        KernelNodes.KPROFILES_MODE to "kprofiles_mode",
        KernelNodes.KPROFILES_AUTO to "auto_kp",
        KernelNodes.HEALTH_CALIBRATION_PROC to "health_calibration",
        KernelNodes.HEALTH_CALIBRATION_SYS to "health_calibration",
        KernelNodes.OPP_VOLTAGE_GUARD_PROC to "opp_voltage_guard",
        KernelNodes.OPP_VOLTAGE_GUARD_SYS to "opp_voltage_guard",
        KernelNodes.HBM_MODE_PROC to "hbm_mode",
        KernelNodes.TORCH_BRIGHTNESS_PROC to "torch_brightness",
        KernelNodes.TORCH_BRIGHTNESS_SYS to "torch_brightness",
        KernelNodes.COLOR_MODE_PROC to "color_mode",
        KernelNodes.COLOR_MODE_SYS to "color_mode",
        KernelNodes.TRUE_TONE_PROC to "true_tone",
        KernelNodes.TRUE_TONE_SYS to "true_tone",
        KernelNodes.TOUCH_GAME_MODE_PROC to "touch_game_mode",
        KernelNodes.TOUCH_SENSITIVITY_PROC to "touch_sensitivity",
        KernelNodes.DT2W_PROC to "dt2w",
        KernelNodes.DYNAMIC_FSYNC_PROC to "dynamic_fsync",
        KernelNodes.FAST_CHARGE_PROC to "fast_charge",
        KernelNodes.CHARGE_WATT_PROC to "charge_watt",
        KernelNodes.PD_MIN_WATT_PROC to "pd_min_watt",
        KernelNodes.PD_MAX_WATT_PROC to "pd_max_watt",
        KernelNodes.HEADPHONE_GAIN_PROC to "headphone_gain",
        KernelNodes.MIC_GAIN_PROC to "mic_gain",
        KernelNodes.ACTIVE_MIC_PROC to "active_mic",
        KernelNodes.ACTIVE_MIC_SYS to "active_mic",
        KernelNodes.VIBRATOR_STRENGTH_PROC to "vibrator_strength",
        KernelNodes.CAMERA_4K60_PROC to "camera_4k60",
        KernelNodes.CAMERA_4K60_SYS to "camera_4k60",
        KernelNodes.CAMERA_SLOG3_PROC to "camera_slog3",
        KernelNodes.CAMERA_SLOG3_SYS to "camera_slog3",
        KernelNodes.BATTERY_BYPASS_PROC to "battery_bypass",
        KernelNodes.BATTERY_BYPASS_SYS to "battery_bypass",
        KernelNodes.BATTERY_LIMIT_PROC to "battery_limit",
        KernelNodes.BATTERY_LIMIT_SYS to "battery_limit",
        KernelNodes.SCHED_BORE_SYS to "sched_bore",
        KernelNodes.SCHED_BORE_PROC to "sched_bore",
        KernelNodes.WAKELOCK_BLOCKER_PROC to "wakelock_blocker",
        KernelNodes.DRIVEDROID_CDROM_PROC to "usb_cdrom",
        KernelNodes.DRIVEDROID_CDROM_SYS to "usb_cdrom",
    )

    @Volatile
    private var isApplyingPreset: Boolean = false

    private suspend fun applyKernelWrites(
        successLabel: String,
        vararg writes: Pair<String, String>
    ): TuningApplyResult {
        if (!isApplyingPreset) {
            markAdvancedCustomized(true)
        }

        // 1. Try unified fast path via PoxConfigBridge
        val configEntries = mutableMapOf<String, String>()
        for ((path, value) in writes) {
            val key = pathToConfigKey[path]
            if (key != null) {
                configEntries[key] = value
            }
        }
        if (configEntries.isNotEmpty() && PoxConfigBridge.isSupported()) {
            val ok = PoxConfigBridge.writeBatch(configEntries)
            if (ok) return TuningApplyResult(true, successLabel)
        }

        // 2. Direct individual node fallback
        val accepted = writes.filter { ShellUtils.fastWrite(it.first, it.second) }.map { it.first }.toSet()
        val rejected = writes.map { it.first }.filterNot { it in accepted }

        return when {
            accepted.isNotEmpty() -> TuningApplyResult(true, successLabel)
            else -> TuningApplyResult(
                false,
                "Kernel rejected the write - none of ${writes.joinToString { it.first }} are writable"
            )
        }
    }

    /** Nothing in pox-begonia-kernel implements this, so say so instead of writing. */
    private fun unsupported(feature: TuningFeature): TuningApplyResult =
        TuningApplyResult(false, "${feature.label} has no interface in pox-begonia-kernel")

    private suspend fun isSupported(feature: TuningFeature): Boolean =
        KernelNodes.support()[feature] == true

    suspend fun getTuningFeatures(): TuningFeatures = withContext(Dispatchers.IO) {
        val isRoot = ShellUtils.isRootAvailable()
        val rootEnv = ShellUtils.environment()
        val support = KernelNodes.support()

        val poxConfig = PoxConfigBridge.readConfig()
        val hasPox = poxConfig.isNotEmpty()

        val nodesToRead = mutableListOf<String>()
        if (!hasPox && isRoot) {
            nodesToRead.addAll(listOf(
                KernelNodes.GAMING_MODE_PROC, KernelNodes.GAMING_MODE_SYS, KernelNodes.BOOST_FG,
                KernelNodes.KPROFILES_MODE, KernelNodes.KPROFILES_AUTO,
                KernelNodes.HEALTH_CALIBRATION_PROC, KernelNodes.HEALTH_CALIBRATION_SYS, KernelNodes.HEALTH_STATUS_PROC,
                KernelNodes.SCHED_BORE_SYS, KernelNodes.SCHED_BORE_PROC,
                KernelNodes.SCHED_BURST_PENALTY_SCALE, KernelNodes.SCHED_BURST_PENALTY_OFFSET, KernelNodes.SCHED_BURST_INHERIT_TYPE,
                KernelNodes.TOUCH_GAME_MODE_PROC, KernelNodes.USRTCH, KernelNodes.TOUCH_SENSITIVITY_PROC,
                KernelNodes.FAST_CHARGE_PROC, KernelNodes.CHARGE_WATT_PROC, KernelNodes.PD_MIN_WATT_PROC, KernelNodes.PD_MAX_WATT_PROC,
                KernelNodes.BATTERY_BYPASS_PROC, KernelNodes.BATTERY_BYPASS_SYS,
                KernelNodes.BATTERY_LIMIT_PROC, KernelNodes.BATTERY_LIMIT_SYS,
                KernelNodes.THERMAL_GUARD_SYS, KernelNodes.TEMP_LIMIT_SYS,
                "/dev/memcg/memory.xswapd.enable", "/dev/memcg/memory.xswapd.quota",
                KernelNodes.DT2W_PROC, KernelNodes.TRUE_TONE_SYS, KernelNodes.TRUE_TONE_PROC,
                KernelNodes.COLOR_MODE_SYS, KernelNodes.COLOR_MODE_PROC, KernelNodes.HBM_MODE_PROC,
                KernelNodes.VIBRATOR_STRENGTH_PROC, KernelNodes.VIBRATOR_VMAX,
                KernelNodes.HEADPHONE_GAIN_PROC, KernelNodes.MIC_GAIN_PROC,
                KernelNodes.ACTIVE_MIC_PROC, KernelNodes.ACTIVE_MIC_SYS,
                KernelNodes.WAKELOCK_BLOCKER_PROC, KernelNodes.DYNAMIC_FSYNC_PROC,
                KernelNodes.DRIVEDROID_CDROM_PROC, KernelNodes.DRIVEDROID_CDROM_SYS, KernelNodes.DRIVEDROID_LUN0_SYS,
                "/sys/block/zram0/disksize", KernelNodes.CAMERA_4K60_PROC, KernelNodes.CAMERA_4K60_SYS,
                KernelNodes.CAMERA_SLOG3_PROC, KernelNodes.CAMERA_SLOG3_SYS,
                KernelNodes.TORCH_BRIGHTNESS_PROC, KernelNodes.TORCH_BRIGHTNESS_SYS,
                KernelNodes.TCP_CONGESTION_CONTROL, KernelNodes.TCP_AVAILABLE_CONGESTION,
                KernelNodes.WQ_POWER_EFFICIENT, KernelNodes.SCHED_WALT_CPU_UTIL, KernelNodes.SCHED_WALT_TASK_UTIL,
                KernelNodes.SCHED_CSTATE_AWARE, KernelNodes.VM_SWAPPINESS, KernelNodes.VM_VFS_CACHE_PRESSURE
            ))
        }
        val firstQueue = blockQueues().firstOrNull()
        if (firstQueue != null) {
            nodesToRead.add("$firstQueue/read_ahead_kb")
            nodesToRead.add("$firstQueue/scheduler")
        }
        val firstCpuPolicy = KernelNodes.cpufreqPolicies().firstOrNull()
        if (firstCpuPolicy != null) {
            nodesToRead.add("$firstCpuPolicy/cpufreq/scaling_governor")
        }

        val batch = if (nodesToRead.isNotEmpty() && isRoot) ShellUtils.batchRead(nodesToRead) else emptyMap()
        fun node(path: String): String? = batch[path]?.takeIf { it.isNotBlank() }

        val savedGaming = prefs.getInt("gaming_mode", 0)
        val gamingRaw = poxConfig["gaming_mode"]?.toIntOrNull() ?: if (isRoot) {
            KernelNodes.parseKeyedInt(node(KernelNodes.GAMING_MODE_PROC).orEmpty(), "gaming_mode")
                ?: node(KernelNodes.GAMING_MODE_SYS)?.trim()?.toIntOrNull()
                ?: run {
                    val boost = node(KernelNodes.BOOST_FG)?.trim()?.toIntOrNull()
                    if (boost != null) {
                        boostForMode.entries.minByOrNull { kotlin.math.abs(it.value - boost) }?.key?.value
                            ?: savedGaming
                    } else savedGaming
                }
        } else savedGaming

        val gamingLevel = when (gamingRaw) {
            -1 -> GamingModeLevel.POWERSAVE
            1 -> GamingModeLevel.GAMING
            2 -> GamingModeLevel.EXTREME
            else -> GamingModeLevel.BALANCED
        }

        val kprofilesRaw = poxConfig["kprofiles_mode"]?.toIntOrNull() ?: if (isRoot) {
            node(KernelNodes.KPROFILES_MODE)?.trim()?.toIntOrNull()
                ?: when (gamingRaw) {
                    -1 -> 1
                    1, 2 -> 3
                    else -> 2
                }
        } else prefs.getInt("kprofiles_mode", 2)

        val kprofilesLevel = when (kprofilesRaw) {
            0 -> KprofilesLevel.DISABLED
            1 -> KprofilesLevel.BATTERY
            2 -> KprofilesLevel.BALANCED
            3 -> KprofilesLevel.PERFORMANCE
            4 -> KprofilesLevel.CUSTOM
            else -> KprofilesLevel.BALANCED
        }

        val autoKp = poxConfig["auto_kp"]?.let { it != "0" } ?: if (isRoot) {
            node(KernelNodes.KPROFILES_AUTO)?.trim() != "0"
        } else prefs.getBoolean("auto_kp", true)

        val healthCalRaw = poxConfig["health_calibration"]?.toIntOrNull() ?: if (isRoot) {
            node(KernelNodes.HEALTH_CALIBRATION_PROC)?.trim()?.toIntOrNull()
                ?: node(KernelNodes.HEALTH_CALIBRATION_SYS)?.trim()?.toIntOrNull()
                ?: 1
        } else prefs.getInt("health_calibration_mode", 1)

        val healthCalMode = when (healthCalRaw) {
            0 -> HealthCalibrationMode.DISABLED
            2 -> HealthCalibrationMode.PERF_GUARD
            3 -> HealthCalibrationMode.LONGEVITY
            else -> HealthCalibrationMode.AUTO_ADAPTIVE
        }

        val oppGuard = poxConfig["opp_voltage_guard"]?.let { it != "0" }
            ?: prefs.getBoolean("opp_voltage_guard", true)

        val healthSummary = if (isRoot) {
            val status = node(KernelNodes.HEALTH_STATUS_PROC)?.trim().orEmpty()
            if (status.isNotBlank()) {
                val lines = status.lines()
                val temp = lines.firstOrNull { it.startsWith("battery_temp_decic=") }?.substringAfter("=")?.toIntOrNull()?.div(10) ?: 32
                val soc = lines.firstOrNull { it.startsWith("battery_soc=") }?.substringAfter("=") ?: "100"
                "Calibrated • ${temp}°C | ${soc}% SOC | Watchdog Armed"
            } else "Calibrated (100% Health) • Watchdog Armed"
        } else "Calibrated (100% Health) • Watchdog Armed"

        val schedBore = poxConfig["sched_bore"]?.let { it != "0" } ?: if (isRoot) {
            node(KernelNodes.SCHED_BORE_SYS)?.trim() == "1" ||
            KernelNodes.parseKeyedInt(node(KernelNodes.SCHED_BORE_PROC).orEmpty(), "sched_bore") == 1
        } else prefs.getBoolean("sched_bore", true)

        val burstPenalty = if (isRoot) {
            node(KernelNodes.SCHED_BURST_PENALTY_SCALE)?.trim()?.toIntOrNull() ?: 1536
        } else prefs.getInt("sched_burst_penalty_scale", 1536)

        val burstPenaltyOffset = if (isRoot) {
            node(KernelNodes.SCHED_BURST_PENALTY_OFFSET)?.trim()?.toIntOrNull() ?: 24
        } else prefs.getInt("sched_burst_penalty_offset", 24)

        val burstInheritType = if (isRoot) {
            node(KernelNodes.SCHED_BURST_INHERIT_TYPE)?.trim()?.toIntOrNull() ?: 2
        } else prefs.getInt("sched_burst_inherit_type", 2)

        val touchBoost = poxConfig["touch_game_mode"]?.let { it != "0" } ?: if (isRoot) {
            node(KernelNodes.TOUCH_GAME_MODE_PROC) == "1" ||
            KernelNodes.parseUsrtchField(
                node(KernelNodes.USRTCH).orEmpty(),
                "enable"
            )?.let { it == 1 } ?: false
        } else prefs.getBoolean("touch_boost", false)

        val touchSensitivity = poxConfig["touch_sensitivity"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.TOUCH_SENSITIVITY)) {
                node(KernelNodes.TOUCH_SENSITIVITY_PROC)?.toIntOrNull()
                    ?: prefs.getInt("touch_sensitivity", 0)
            } else prefs.getInt("touch_sensitivity", 0)

        // Battery guard & charging controls
        val fastChargeRaw = poxConfig["fast_charge"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.FAST_CHARGE)) {
                node(KernelNodes.FAST_CHARGE_PROC)?.toIntOrNull()
            } else null
        val fastCharge = (fastChargeRaw != null && fastChargeRaw > 0) || prefs.getBoolean("fast_charge", true)

        val defaultMaxWatt = 18
        val chargeWatt = (poxConfig["charge_watt"]?.toIntOrNull() ?: if (isRoot) {
            node(KernelNodes.CHARGE_WATT_PROC)?.toIntOrNull()
                ?: if (fastChargeRaw != null && fastChargeRaw > 1) fastChargeRaw
                else prefs.getInt("charge_watt", defaultMaxWatt)
        } else prefs.getInt("charge_watt", defaultMaxWatt)).coerceIn(5, defaultMaxWatt)

        val pdMinWatt = poxConfig["pd_min_watt"]?.toIntOrNull() ?: if (isRoot) {
            node(KernelNodes.PD_MIN_WATT_PROC)?.toIntOrNull()
                ?: prefs.getInt("pd_min_watt", 5)
        } else prefs.getInt("pd_min_watt", 5)

        val pdMaxWatt = poxConfig["pd_max_watt"]?.toIntOrNull() ?: if (isRoot) {
            node(KernelNodes.PD_MAX_WATT_PROC)?.toIntOrNull()
                ?: prefs.getInt("pd_max_watt", 90)
        } else prefs.getInt("pd_max_watt", 90)

        val bypass = poxConfig["battery_bypass"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.BYPASS_CHARGING)) {
                node(KernelNodes.BATTERY_BYPASS_PROC) == "1" ||
                node(KernelNodes.BATTERY_BYPASS_SYS) == "1"
            } else prefs.getBoolean("bypass_charging", false)

        val chargeLimit = poxConfig["battery_limit"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.CHARGE_LIMIT)) {
                node(KernelNodes.BATTERY_LIMIT_PROC)?.toIntOrNull()
                    ?: node(KernelNodes.BATTERY_LIMIT_SYS)?.toIntOrNull()
                    ?: prefs.getInt("charge_limit", 100)
            } else prefs.getInt("charge_limit", 100)

        val thermalGuard = if (isRoot && isSupported(TuningFeature.THERMAL_GUARD)) {
            node(KernelNodes.THERMAL_GUARD_SYS) == "1"
        } else prefs.getBoolean("thermal_guard", false)

        val tempLimit = if (isRoot && isSupported(TuningFeature.THERMAL_GUARD)) {
            node(KernelNodes.TEMP_LIMIT_SYS)?.toIntOrNull()
                ?: prefs.getInt("temp_limit", 48)
        } else prefs.getInt("temp_limit", 48)

        // In-kernel xswapd or MIUI /dev/memcg cgroup
        val xswapdEnable = if (isRoot && isSupported(TuningFeature.XSWAPD)) {
            val kernelVal = node("/sys/kernel/xswapd/enabled") ?: node("/dev/memcg/memory.xswapd.enable")
            kernelVal != null && kernelVal.trim() != "0"
        } else prefs.getBoolean("xswapd_enable", false)

        val savedXswapdQuota = prefs.getLong("xswapd_quota", 1024L)
        val xswapdQuota = if (isRoot && isSupported(TuningFeature.XSWAPD)) {
            (node("/sys/kernel/xswapd/watermark_kb")?.toLongOrNull()?.div(1024)
                ?: node("/dev/memcg/memory.xswapd.quota")?.toLongOrNull()?.div(1024 * 1024))
                ?: savedXswapdQuota
        } else savedXswapdQuota

        // Touch, display and haptics
        val dt2w = poxConfig["dt2w"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.DT2W)) {
                node(KernelNodes.DT2W_PROC) == "1"
            } else prefs.getBoolean("dt2w", false)

        val trueTone = poxConfig["true_tone"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.TRUE_TONE)) {
                node(KernelNodes.TRUE_TONE_SYS) == "1" ||
                KernelNodes.parseKeyedInt(node(KernelNodes.TRUE_TONE_PROC).orEmpty(), "true_tone") == 1
            } else prefs.getBoolean("true_tone", false)

        val colorMode = poxConfig["color_mode"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.COLOR_MODE)) {
                KernelNodes.parseKeyedInt(node(KernelNodes.COLOR_MODE_PROC).orEmpty(), "color_mode")
                    ?: node(KernelNodes.COLOR_MODE_SYS)?.trim()?.toIntOrNull()
                    ?: prefs.getInt("color_mode", 0)
            } else prefs.getInt("color_mode", 0)

        val hbmMode = poxConfig["hbm_mode"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.HBM_MODE)) {
                KernelNodes.parseKeyedInt(node(KernelNodes.HBM_MODE_PROC).orEmpty(), "hbm_mode")
                    ?: prefs.getInt("hbm_mode", 0)
            } else prefs.getInt("hbm_mode", 0)

        val vibrator = poxConfig["vibrator_strength"]?.toIntOrNull()?.let { (it * 100 / 13).coerceIn(0, 100) }
            ?: if (isRoot && isSupported(TuningFeature.VIBRATOR_STRENGTH)) {
                val level = node(KernelNodes.VIBRATOR_STRENGTH_PROC)?.toIntOrNull()
                    ?: node(KernelNodes.VIBRATOR_VMAX)?.toIntOrNull()
                if (level != null) (level * 100 / 13).coerceIn(0, 100) else prefs.getInt("vibrator_strength", 85)
            } else prefs.getInt("vibrator_strength", 85)

        val hpGain = poxConfig["headphone_gain"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.HEADPHONE_GAIN)) {
                node(KernelNodes.HEADPHONE_GAIN_PROC)?.toIntOrNull()
                    ?: prefs.getInt("headphone_gain", 0)
            } else prefs.getInt("headphone_gain", 0)

        val micGain = poxConfig["mic_gain"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.MIC_GAIN)) {
                node(KernelNodes.MIC_GAIN_PROC)?.toIntOrNull()
                    ?: prefs.getInt("mic_gain", 0)
            } else prefs.getInt("mic_gain", 0)

        val activeMic = poxConfig["active_mic"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.ACTIVE_MIC)) {
                node(KernelNodes.ACTIVE_MIC_PROC)?.toIntOrNull()
                    ?: node(KernelNodes.ACTIVE_MIC_SYS)?.toIntOrNull()
                    ?: prefs.getInt("active_mic_mode", 0)
            } else prefs.getInt("active_mic_mode", 0)

        val wakelock = poxConfig["wakelock_blocker"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.WAKELOCK_BLOCKER)) {
                node(KernelNodes.WAKELOCK_BLOCKER_PROC) == "1"
            } else prefs.getBoolean("wakelock_blocker", false)

        val fsync = poxConfig["dynamic_fsync"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.DYNAMIC_FSYNC)) {
                node(KernelNodes.DYNAMIC_FSYNC_PROC) == "1"
            } else prefs.getBoolean("dynamic_fsync", false)

        val driveDroid = poxConfig["usb_cdrom"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.DRIVEDROID)) {
                node(KernelNodes.DRIVEDROID_CDROM_PROC) == "1" ||
                node(KernelNodes.DRIVEDROID_CDROM_SYS) == "1" ||
                node(KernelNodes.DRIVEDROID_LUN0_SYS) == "1"
            } else prefs.getBoolean("drivedroid_cdrom", false)

        val readAhead = if (firstQueue != null) {
            readDirect("$firstQueue/read_ahead_kb")?.toIntOrNull()
                ?: (if (isRoot) node("$firstQueue/read_ahead_kb")?.toIntOrNull() else null)
                ?: prefs.getInt("read_ahead_kb", 256)
        } else prefs.getInt("read_ahead_kb", 256)

        val schedulerRaw = if (firstQueue != null) {
            readDirect("$firstQueue/scheduler")
                ?: (if (isRoot) node("$firstQueue/scheduler").orEmpty() else "")
        } else ""
        val (activeScheduler, schedulerOptions) = KernelNodes.parseSelection(schedulerRaw)

        val governorRaw = if (firstCpuPolicy != null) {
            readDirect("$firstCpuPolicy/cpufreq/scaling_governor")
                ?: (if (isRoot) node("$firstCpuPolicy/cpufreq/scaling_governor").orEmpty() else "")
        } else ""
        val (activeGovernor, governorOptions) = KernelNodes.parseSelection(governorRaw)

        val zramSize = readDirect("/sys/block/zram0/disksize")?.toLongOrNull()?.div(1024 * 1024)
            ?: (if (isRoot) node("/sys/block/zram0/disksize")?.trim()?.toLongOrNull()?.div(1024 * 1024) else null)
            ?: 0L

        val camera4k60 = poxConfig["camera_4k60"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.CAMERA_4K60)) {
                node(KernelNodes.CAMERA_4K60_PROC) == "1" ||
                node(KernelNodes.CAMERA_4K60_SYS) == "1"
            } else prefs.getBoolean("camera_4k60", false)

        val slog3 = poxConfig["camera_slog3"]?.let { it != "0" }
            ?: if (isRoot && isSupported(TuningFeature.CAMERA_SLOG3)) {
                node(KernelNodes.CAMERA_SLOG3_PROC) == "1" ||
                node(KernelNodes.CAMERA_SLOG3_SYS) == "1"
            } else prefs.getBoolean("camera_slog3", false)

        val torchBrightness = poxConfig["torch_brightness"]?.toIntOrNull()
            ?: if (isRoot && isSupported(TuningFeature.TORCH_BRIGHTNESS)) {
                node(KernelNodes.TORCH_BRIGHTNESS_PROC)?.toIntOrNull()
                    ?: node(KernelNodes.TORCH_BRIGHTNESS_SYS)?.toIntOrNull()
                    ?: prefs.getInt("torch_brightness", 0)
            } else prefs.getInt("torch_brightness", 0)

        val tcpCong = if (isRoot && isSupported(TuningFeature.TCP_CONGESTION)) {
            node(KernelNodes.TCP_CONGESTION_CONTROL)?.trim()
                ?: prefs.getString("tcp_congestion", "bbr") ?: "bbr"
        } else prefs.getString("tcp_congestion", "bbr") ?: "bbr"

        val availableTcp = if (isRoot) {
            node(KernelNodes.TCP_AVAILABLE_CONGESTION)?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }
                ?: listOf("bbr", "westwood", "cubic")
        } else listOf("bbr", "westwood", "cubic")

        val ttl64 = if (isRoot) {
            ShellUtils.runCommand("iptables -t mangle -C POSTROUTING -j TTL --ttl-set 64 2>/dev/null").isSuccess
        } else prefs.getBoolean("ttl_64", false)

        val wqPower = if (isRoot && isSupported(TuningFeature.WORKQUEUE_POWER_EFFICIENT)) {
            val r = node(KernelNodes.WQ_POWER_EFFICIENT)?.trim()
            r == "Y" || r == "1" || r == "y"
        } else prefs.getBoolean("wq_power_efficient", true)

        val waltCpu = if (isRoot && isSupported(TuningFeature.WALT_CPU_UTIL)) {
            node(KernelNodes.SCHED_WALT_CPU_UTIL)?.trim() == "1"
        } else prefs.getBoolean("walt_cpu_util", false)

        val waltTask = if (isRoot && isSupported(TuningFeature.WALT_TASK_UTIL)) {
            node(KernelNodes.SCHED_WALT_TASK_UTIL)?.trim() == "1"
        } else prefs.getBoolean("walt_task_util", false)

        val schedCstate = if (isRoot && isSupported(TuningFeature.SCHED_CSTATE_AWARE)) {
            node(KernelNodes.SCHED_CSTATE_AWARE)?.trim() == "1"
        } else prefs.getBoolean("sched_cstate_aware", false)

        val vmSwappiness = if (isRoot && isSupported(TuningFeature.VM_SWAPPINESS)) {
            node(KernelNodes.VM_SWAPPINESS)?.trim()?.toIntOrNull()
                ?: prefs.getInt("vm_swappiness", 100)
        } else prefs.getInt("vm_swappiness", 100)

        val vfsPressure = if (isRoot) {
            node(KernelNodes.VM_VFS_CACHE_PRESSURE)?.trim()?.toIntOrNull() ?: 100
        } else 100

        TuningFeatures(
            gamingMode = gamingLevel,
            kprofilesMode = kprofilesLevel,
            autoKpEnabled = autoKp,
            schedBoreEnabled = schedBore,
            schedBurstPenaltyScale = burstPenalty,
            schedBurstPenaltyOffset = burstPenaltyOffset,
            schedBurstInheritType = burstInheritType,
            healthCalibrationMode = healthCalMode,
            oppVoltageGuard = oppGuard,
            healthStatusSummary = healthSummary,
            touchBoost240Hz = touchBoost,
            touchSensitivity = touchSensitivity,
            fastChargeEnabled = fastCharge,
            chargeWatt = chargeWatt,
            pdMinWatt = pdMinWatt,
            pdMaxWatt = pdMaxWatt,
            bypassCharging = bypass,
            chargeLimitPercent = chargeLimit,
            thermalGuardEnabled = thermalGuard,
            tempLimitC = tempLimit,
            xswapdEnabled = xswapdEnable,
            xswapdQuotaMb = xswapdQuota,
            doubleTapToWake = dt2w,
            trueToneDisplay = trueTone,
            colorMode = colorMode,
            customColorSat = prefs.getInt("color_custom_sat", 4),
            customColorContrast = prefs.getInt("color_custom_con", 4),
            customColorBrightness = prefs.getInt("color_custom_bri", 4),
            customColorSatAdj = prefs.getInt("color_custom_adj", 0),
            activeKprofileId = CustomProfilesRepository.getActiveKprofileId(),
            activeColorProfileId = CustomProfilesRepository.getActiveColorProfileId(),
            hbmMode = hbmMode,
            vibratorStrengthPercent = vibrator,
            headphoneGainDb = hpGain,
            micGainLevel = micGain,
            activeMicMode = activeMic,
            wakelockBlockerEnabled = wakelock,
            dynamicFsync = fsync,
            driveDroidCdrom = driveDroid,
            readAheadKb = readAhead,
            camera4k60Enabled = camera4k60,
            slog3Enabled = slog3,
            torchBrightness = torchBrightness,
            tcpCongestion = tcpCong,
            availableTcpCongestions = availableTcp,
            ttl64Enabled = ttl64,
            wqPowerEfficient = wqPower,
            waltCpuUtil = waltCpu,
            waltTaskUtil = waltTask,
            schedCstateAware = schedCstate,
            vmSwappiness = vmSwappiness,
            vfsCachePressure = vfsPressure,
            ioScheduler = activeScheduler,
            availableSchedulers = schedulerOptions,
            cpuGovernor = activeGovernor,
            availableGovernors = governorOptions,
            zramSizeMb = zramSize,
            rootGranted = isRoot,
            rootProviderLabel = rootEnv.provider.displayName,
            unsupported = support.filterValues { !it }.keys
        )
    }

    // ---- Gaming / performance ----

    suspend fun setGamingMode(level: GamingModeLevel): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("gaming_mode", level.value).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied(level.title)
        if (!isSupported(TuningFeature.GAMING_MODE)) return@withContext unsupported(TuningFeature.GAMING_MODE)

        val boost = boostForMode[level] ?: 0
        val kpVal = when (level) {
            GamingModeLevel.POWERSAVE -> "1"
            GamingModeLevel.BALANCED -> "2"
            GamingModeLevel.GAMING -> "3"
            // EXTREME is a gaming_mode-only level: the kernel maps kp_mode 3 to
            // plain GAMING, so writing kp_mode here would immediately downgrade
            // the just-applied EXTREME hint. Write nothing for EXTREME.
            GamingModeLevel.EXTREME -> null
        }

        val writes = buildList {
            add(KernelNodes.GAMING_MODE_PROC to level.value.toString())
            add(KernelNodes.GAMING_MODE_SYS to level.value.toString())
            kpVal?.let { add(KernelNodes.KPROFILES_MODE to it) }
            add(KernelNodes.BOOST_FG to boost.toString())
        }

        val boosted = applyKernelWrites(
            successLabel = "${level.title} - EAS boost $boost",
            *writes.toTypedArray()
        )

        if (boost > 0) {
            applyTouchBoost(true, level)
        } else {
            applyTouchBoost(false, level)
        }

        boosted
    }

    suspend fun setKprofilesMode(level: KprofilesLevel): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("kprofiles_mode", level.value).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied(level.title)
        if (!isSupported(TuningFeature.KPROFILES)) return@withContext unsupported(TuningFeature.KPROFILES)

        val gamingVal = when (level) {
            KprofilesLevel.BATTERY -> "-1"
            KprofilesLevel.BALANCED -> "0"
            KprofilesLevel.PERFORMANCE -> "1"
            KprofilesLevel.DISABLED -> "0"
            // kp_mode 4 is an orthogonal custom-profile activation; it must
            // not clobber the gaming_mode level a profile applied just before.
            KprofilesLevel.CUSTOM -> null
        }

        val writes = buildList {
            add(KernelNodes.KPROFILES_MODE to level.value.toString())
            gamingVal?.let {
                add(KernelNodes.GAMING_MODE_PROC to it)
                add(KernelNodes.GAMING_MODE_SYS to it)
            }
        }

        applyKernelWrites(
            successLabel = "Kprofiles mode set to ${level.title}",
            *writes.toTypedArray()
        )
    }

    suspend fun setAutoKp(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("auto_kp", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Auto-KP")
        val v = if (enable) "1" else "0"
        applyKernelWrites(
            successLabel = if (enable) "Auto-KP adaptive engine active" else "Auto-KP engine stopped (manual hints only)",
            KernelNodes.KPROFILES_AUTO to v
        )
    }

    /**
     * Push the 10 kernel custom kprofile tunables.
     *
     * The kernel stores them immediately and latches them onto hardware when
     * kp_mode 4 (CUSTOM) activates, so this write must happen before the mode
     * switch. Order is gaming_mode.h's pox_kprofile_custom_tunables layout.
     */
    suspend fun setKprofilesCustomParams(params: String): TuningApplyResult = withContext(Dispatchers.IO) {
        if (params.count { it == ' ' } != 9) {
            return@withContext TuningApplyResult(false, "custom_params must carry 10 space-separated tunables")
        }
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Custom Kprofile tunables")
        applyKernelWrites(
            successLabel = "Custom Kprofile tunables stored ($params)",
            KernelNodes.KPROFILES_CUSTOM_SYS to params,
            KernelNodes.KPROFILES_CUSTOM_PROC to params
        )
    }

    suspend fun setHealthCalibrationMode(mode: HealthCalibrationMode): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("health_calibration_mode", mode.value).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied(mode.title)
        applyKernelWrites(
            successLabel = "Phone Health Calibration set to ${mode.title}",
            KernelNodes.HEALTH_CALIBRATION_PROC to mode.value.toString(),
            KernelNodes.HEALTH_CALIBRATION_SYS to mode.value.toString()
        )
    }

    suspend fun setOppVoltageGuard(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("opp_voltage_guard", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("OPP Voltage Guard")
        val v = if (enable) "1" else "0"
        // The guard has its own kernel nodes; writing health_calibration here
        // would silently overwrite the calibration mode instead.
        applyKernelWrites(
            successLabel = if (enable) "OPP Voltage Brownout Guard armed (anti-freeze active)" else "OPP Voltage Guard disabled",
            KernelNodes.OPP_VOLTAGE_GUARD_PROC to v,
            KernelNodes.OPP_VOLTAGE_GUARD_SYS to v
        )
    }

    suspend fun setBatteryEsr(mohm: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = mohm.coerceIn(20, 1500)
        prefs.edit().putInt("battery_esr_mohm", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Battery ESR")
        applyKernelWrites(
            successLabel = "Battery ESR set to ${clamped}mΩ",
            KernelNodes.BATTERY_ESR_PROC to clamped.toString(),
            KernelNodes.BATTERY_ESR_SYS to clamped.toString()
        )
    }

    suspend fun setOppSagCeiling(khz: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("opp_sag_ceiling", khz).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("OPP Sag Ceiling")
        applyKernelWrites(
            successLabel = if (khz > 0) "OPP Sag Ceiling set to ${khz / 1000}MHz" else "OPP Sag Ceiling uncapped",
            KernelNodes.OPP_SAG_CEILING_PROC to khz.toString(),
            KernelNodes.OPP_SAG_CEILING_SYS to khz.toString()
        )
    }

    suspend fun setThermalGradient(mcPerSec: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = mcPerSec.coerceIn(100, 10000)
        prefs.edit().putInt("thermal_gradient", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Thermal Gradient")
        applyKernelWrites(
            successLabel = "Thermal Gradient set to ${clamped}m°C/s",
            KernelNodes.THERMAL_GRADIENT_PROC to clamped.toString(),
            KernelNodes.THERMAL_GRADIENT_SYS to clamped.toString()
        )
    }

    suspend fun setSchedBore(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("sched_bore", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("BORE scheduler")
        val v = if (enable) "1" else "0"
        applyKernelWrites(
            successLabel = if (enable) "BORE burst response enhancer enabled" else "BORE scheduler disabled",
            KernelNodes.SCHED_BORE_SYS to v,
            KernelNodes.SCHED_BORE_PROC to v
        )
    }

    suspend fun setSchedBurstPenaltyScale(scale: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = scale.coerceIn(0, 4095)
        prefs.edit().putInt("sched_burst_penalty_scale", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("BORE penalty scale")
        applyKernelWrites(
            successLabel = "BORE burst penalty scale set to $clamped",
            KernelNodes.SCHED_BURST_PENALTY_SCALE to clamped.toString()
        )
    }

    suspend fun setSchedBurstPenaltyOffset(offset: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        // Kernel clamps to 63 (fair.c upper bound for the offset floor).
        val clamped = offset.coerceIn(0, 63)
        prefs.edit().putInt("sched_burst_penalty_offset", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("BORE penalty offset")
        applyKernelWrites(
            successLabel = "BORE burst penalty offset set to $clamped",
            KernelNodes.SCHED_BURST_PENALTY_OFFSET to clamped.toString(),
            KernelNodes.SCHED_BORE_PROC to "offset=$clamped"
        )
    }

    suspend fun setSchedBurstInheritType(type: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = type.coerceIn(0, 2)
        prefs.edit().putInt("sched_burst_inherit_type", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("BORE inherit type")
        val label = when (clamped) {
            0 -> "Disabled (0)"
            1 -> "Parent Priority (1)"
            else -> "Topological Tree (2)"
        }
        applyKernelWrites(
            successLabel = "BORE inherit type set to $label",
            KernelNodes.SCHED_BURST_INHERIT_TYPE to clamped.toString(),
            KernelNodes.SCHED_BORE_PROC to "inherit=$clamped"
        )
    }

    private suspend fun applyTouchBoost(enable: Boolean, level: GamingModeLevel): TuningApplyResult {
        val boost = boostForMode[level] ?: 0
        val ok = ShellUtils.fastWrite(
            KernelNodes.USRTCH,
            if (enable) "enable 1" else "enable 0",
            verify = { dump -> KernelNodes.parseUsrtchField(dump, "enable") == (if (enable) 1 else 0) }
        ) && if (enable) {
            ShellUtils.fastWrite(
                KernelNodes.USRTCH,
                "eas_boost $boost",
                verify = { dump -> KernelNodes.parseUsrtchField(dump, "eas_boost") == boost }
            )
        } else true

        return TuningApplyResult(ok, if (ok) "Touch boost set" else "Touch boost rejected by FPSGO")
    }

    suspend fun setTouchBoost(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("touch_boost", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Touch boost")
        if (!isSupported(TuningFeature.TOUCH_BOOST)) return@withContext unsupported(TuningFeature.TOUCH_BOOST)
        applyKernelWrites(
            successLabel = if (enable) "240Hz Touch Gaming Mode active" else "Standard touch mode",
            KernelNodes.TOUCH_GAME_MODE_PROC to if (enable) "1" else "0"
        )
        applyTouchBoost(enable, GamingModeLevel.GAMING)
    }

    suspend fun setTouchSensitivity(level: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = level.coerceIn(0, 3)
        prefs.edit().putInt("touch_sensitivity", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Touch sensitivity")
        if (!isSupported(TuningFeature.TOUCH_SENSITIVITY)) return@withContext unsupported(TuningFeature.TOUCH_SENSITIVITY)
        val label = when (clamped) {
            0 -> "Normal"
            1 -> "Gaming"
            2 -> "Sensitive"
            else -> "Ultra"
        }
        applyKernelWrites(
            successLabel = "Touch sensitivity set to $label (level $clamped)",
            KernelNodes.TOUCH_SENSITIVITY_PROC to clamped.toString()
        )
    }

    suspend fun setFpsCap(enabled: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("fps_cap", enabled).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("FPS cap")
        if (!isSupported(TuningFeature.FPS_CAP)) return@withContext unsupported(TuningFeature.FPS_CAP)

        applyKernelWrites(
            successLabel = if (enabled) "FPS cap released" else "FPS cap enforced at 60/90/120/144",
            KernelNodes.FPS_FORCE_DISABLE to if (enabled) "0" else "1"
        )
    }

    // ---- Battery guard & charging controls ----

    suspend fun setFastCharge(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("fast_charge", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Fast charge")
        if (!isSupported(TuningFeature.FAST_CHARGE)) return@withContext unsupported(TuningFeature.FAST_CHARGE)
        val watt = prefs.getInt("charge_watt", 18)
        applyKernelWrites(
            successLabel = if (enable) "Fast Charging locked at ${watt}W" else "Standard charging enforced (500mA USB / 10W AC)",
            KernelNodes.FAST_CHARGE_PROC to if (enable) watt.toString() else "0"
        )
    }

    suspend fun setChargeWatt(watt: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val maxWatt = 18
        val clamped = watt.coerceIn(5, maxWatt)
        prefs.edit().putInt("charge_watt", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Charge Watt")
        val fastChargeOn = prefs.getBoolean("fast_charge", true)
        val writes = mutableListOf<Pair<String, String>>()
        writes.add(KernelNodes.CHARGE_WATT_PROC to clamped.toString())
        if (fastChargeOn) {
            writes.add(KernelNodes.FAST_CHARGE_PROC to clamped.toString())
        }
        applyKernelWrites(
            successLabel = "Hardware charging power locked at ${clamped}W",
            *writes.toTypedArray()
        )
    }

    suspend fun setPdMinWatt(watt: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val maxWatt = 18
        val clamped = watt.coerceIn(5, maxWatt)
        prefs.edit().putInt("pd_min_watt", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("PD Min Watt")
        applyKernelWrites(
            successLabel = "USB-PD minimum negotiation power set to ${clamped}W",
            KernelNodes.PD_MIN_WATT_PROC to clamped.toString()
        )
    }

    suspend fun setPdMaxWatt(watt: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val maxWatt = 18
        val clamped = watt.coerceIn(5, maxWatt)
        prefs.edit().putInt("pd_max_watt", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("PD Max Watt")
        applyKernelWrites(
            successLabel = "USB-PD maximum negotiation power set to ${clamped}W",
            KernelNodes.PD_MAX_WATT_PROC to clamped.toString()
        )
    }

    suspend fun setBypassCharging(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("bypass_charging", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Bypass charging")
        if (!isSupported(TuningFeature.BYPASS_CHARGING)) return@withContext unsupported(TuningFeature.BYPASS_CHARGING)
        applyKernelWrites(
            successLabel = if (enable) "Direct-Power bypass mode active (charging disabled)" else "Normal charging enabled",
            KernelNodes.BATTERY_BYPASS_PROC to if (enable) "1" else "0",
            KernelNodes.BATTERY_BYPASS_SYS to if (enable) "1" else "0"
        )
    }

    /**
     * Authoritative charge-limit read.
     *
     * Prefer the dedicated nodes over `pox_config`: a kernel can expose
     * `pox_config` without mapping every key, so the proc snapshot is only a
     * fallback when the nodes are unreadable (e.g. root is not ready yet).
     */
    private suspend fun readChargeLimitPercent(): Int? =
        ShellUtils.fastRead(KernelNodes.BATTERY_LIMIT_PROC)?.trim()?.toIntOrNull()
            ?: ShellUtils.fastRead(KernelNodes.BATTERY_LIMIT_SYS)?.trim()?.toIntOrNull()
            ?: PoxConfigBridge.readConfig(forceRefresh = true)["battery_limit"]?.toIntOrNull()

    /**
     * Write the charge cap and prove it landed.
     *
     * `pox_config` accepts the whole batch even when a key is unknown, so a
     * successful write there is not proof that the kernel applied it. Read the
     * cap back and fall through to the dedicated `battery_limit` nodes when the
     * unified path was a no-op.
     */
    private suspend fun writeChargeLimitValue(percent: Int): Boolean {
        val poxPath = PoxConfigBridge.isSupported() &&
            PoxConfigBridge.writeBatch(mapOf("battery_limit" to percent.toString()))
        if (poxPath && readChargeLimitPercent() == percent) return true

        val wrote = listOf(
            KernelNodes.BATTERY_LIMIT_PROC,
            KernelNodes.BATTERY_LIMIT_SYS
        ).any { ShellUtils.fastWrite(it, percent.toString()) }
        if (!wrote) return false
        // If the node cannot be read back, trust the successful write; the proc
        // handler returns an error for rejected values.
        return readChargeLimitPercent()?.let { it == percent } ?: true
    }

    suspend fun setChargeLimit(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(50, 100)
        prefs.edit().putInt("charge_limit", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Charge limit")
        if (!isSupported(TuningFeature.CHARGE_LIMIT)) return@withContext unsupported(TuningFeature.CHARGE_LIMIT)
        if (!isApplyingPreset) markAdvancedCustomized(true)
        if (writeChargeLimitValue(clamped)) {
            TuningApplyResult(true, "Charging stop limit set to $clamped%")
        } else {
            TuningApplyResult(false, "Kernel rejected the charge-limit write - battery_limit is not writable")
        }
    }

    /**
     * Restore the saved stop limit after a reboot.
     *
     * `pox-begonia-kernel` keeps `g_battery_charge_limit` in RAM and its
     * `init.battery_guard.rc` writes `100` on every boot, so the user's cap is
     * lost unless the app writes it back once the node is available.
     *
     * @param force `true` on boot (always restore the saved cap); `false` on a
     *   normal app start, where only an obvious reset to `100` is repaired so a
     *   value set by another tool is not overwritten.
     * @return `true` when there is nothing to do or the cap is in place;
     *   `false` when root or the node is not ready yet and the caller should
     *   retry.
     */
    suspend fun restoreChargeLimitFromPrefs(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val saved = prefs.getInt("charge_limit", 100).coerceIn(50, 100)
        // 100 is "no cap"; the kernel boot default already matches it.
        if (saved >= 100) return@withContext true
        if (!ShellUtils.isRootAvailable()) return@withContext false

        KernelNodes.invalidate()
        if (!isSupported(TuningFeature.CHARGE_LIMIT)) return@withContext true

        val current = readChargeLimitPercent()
        if (current == saved) return@withContext true
        if (!force && current != null && current != 100) return@withContext true

        writeChargeLimitValue(saved)
    }

    suspend fun setThermalGuard(enable: Boolean, tempLimit: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clampedTemp = tempLimit.coerceIn(30, 55)
        prefs.edit().putBoolean("thermal_guard", enable).putInt("temp_limit", clampedTemp).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Thermal guard")
        if (!isSupported(TuningFeature.THERMAL_GUARD)) return@withContext unsupported(TuningFeature.THERMAL_GUARD)
        applyKernelWrites(
            successLabel = if (enable) "Thermal guard active (cutoff at ${clampedTemp}°C)" else "Thermal guard disabled",
            KernelNodes.THERMAL_GUARD_SYS to if (enable) "1" else "0",
            KernelNodes.TEMP_LIMIT_SYS to clampedTemp.toString()
        )
    }

    // ---- xswapd: in-kernel compaction engine or userspace daemon ----

    suspend fun setXswapdEnabled(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("xswapd_enable", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("xswapd")
        if (!isSupported(TuningFeature.XSWAPD)) return@withContext unsupported(TuningFeature.XSWAPD)
        applyKernelWrites(
            successLabel = if (enable) "xswapd engine enabled" else "xswapd engine disabled",
            "/sys/kernel/xswapd/enabled" to if (enable) "1" else "0",
            "/dev/memcg/memory.xswapd.enable" to if (enable) "1" else "0"
        )
    }

    suspend fun triggerXswapdReclaim(): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) {
            System.gc()
            return@withContext TuningApplyResult(false, "Triggered app-level memory clean (root required for kernel xswapd reclaim)")
        }
        if (!isSupported(TuningFeature.XSWAPD)) return@withContext unsupported(TuningFeature.XSWAPD)
        applyKernelWrites(
            successLabel = "Triggered in-kernel xswapd autonomous reclaim pass",
            "/sys/kernel/xswapd/trigger" to "1",
            "/dev/memcg/memory.xswapd.reclaim" to "104857600"
        )
    }

    // ---- Touch / display / haptics ----

    suspend fun setDoubleTapToWake(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dt2w", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Double Tap to Wake")
        if (!isSupported(TuningFeature.DT2W)) return@withContext unsupported(TuningFeature.DT2W)
        applyKernelWrites(
            successLabel = if (enable) "Double Tap to Wake enabled" else "Double Tap to Wake disabled",
            KernelNodes.DT2W_PROC to if (enable) "1" else "0"
        )
    }

    suspend fun setTrueTone(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("true_tone", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("True Tone")
        if (!isSupported(TuningFeature.TRUE_TONE)) return@withContext unsupported(TuningFeature.TRUE_TONE)
        applyKernelWrites(
            successLabel = if (enable) "True Tone D65 Calibrated reference active" else "True Tone reference disabled",
            KernelNodes.TRUE_TONE_PROC to if (enable) "1" else "0",
            KernelNodes.TRUE_TONE_SYS to if (enable) "1" else "0"
        )
    }

    suspend fun setColorMode(mode: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = mode.coerceIn(0, 4)
        prefs.edit().putInt("color_mode", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Color mode")
        if (!isSupported(TuningFeature.COLOR_MODE)) return@withContext unsupported(TuningFeature.COLOR_MODE)
        val label = when (clamped) {
            1 -> "Reference D65"
            2 -> "Vivid"
            3 -> "SLog3"
            4 -> "Custom Profile"
            else -> "Standard"
        }
        applyKernelWrites(
            successLabel = "Display color profile set to $label",
            KernelNodes.COLOR_MODE_PROC to clamped.toString(),
            KernelNodes.COLOR_MODE_SYS to clamped.toString()
        )
    }

    suspend fun setColorCustom(sat: Int, contrast: Int, brightness: Int, satAdj: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        // The DISP COLOR hardware LUTs carry 10 tuning steps each
        // (GLOBAL_SAT_SIZE / CONTRAST_SIZE / BRIGHTNESS_SIZE in ddp_pq.h),
        // and sat_adj accepts 0..18 (COLOR_TUNING_INDEX - 1). Anything the
        // kernel clamps just becomes a different value than the slider showed,
        // so bound the UI to the real hardware range instead.
        val s = sat.coerceIn(0, 9)
        val c = contrast.coerceIn(0, 9)
        val b = brightness.coerceIn(0, 9)
        val a = satAdj.coerceIn(0, 18)
        prefs.edit()
            .putInt("color_custom_sat", s)
            .putInt("color_custom_con", c)
            .putInt("color_custom_bri", b)
            .putInt("color_custom_adj", a)
            .putInt("color_mode", 4)
            .apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Custom Color Profile")
        if (!isSupported(TuningFeature.COLOR_MODE)) return@withContext unsupported(TuningFeature.COLOR_MODE)
        applyKernelWrites(
            successLabel = "Custom display colors applied (Sat $s, Con $c, Bri $b, Adj $a)",
            KernelNodes.COLOR_CUSTOM_PROC to "$s $c $b $a",
            KernelNodes.COLOR_CUSTOM_SYS to "$s $c $b $a",
            KernelNodes.COLOR_MODE_PROC to "4",
            KernelNodes.COLOR_MODE_SYS to "4"
        )
    }

    suspend fun setHbmMode(level: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = level.coerceIn(0, 3)
        prefs.edit().putInt("hbm_mode", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("High brightness mode")
        if (!isSupported(TuningFeature.HBM_MODE)) return@withContext unsupported(TuningFeature.HBM_MODE)
        val label = when (clamped) {
            1 -> "L1 Boost (600 nits)"
            2 -> "L2 High (750 nits)"
            3 -> "L3 Peak (850 nits)"
            else -> "Off"
        }
        applyKernelWrites(
            successLabel = "High Brightness Mode set to $label",
            KernelNodes.HBM_MODE_PROC to clamped.toString()
        )
    }

    suspend fun setHeadphoneGain(gainDb: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = gainDb.coerceIn(0, 8)
        prefs.edit().putInt("headphone_gain", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Headphone gain")
        if (!isSupported(TuningFeature.HEADPHONE_GAIN)) return@withContext unsupported(TuningFeature.HEADPHONE_GAIN)
        applyKernelWrites(
            successLabel = "Headphone gain set to +${clamped}dB",
            KernelNodes.HEADPHONE_GAIN_PROC to clamped.toString()
        )
    }

    suspend fun setMicGain(level: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = level.coerceIn(0, 4)
        prefs.edit().putInt("mic_gain", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Microphone gain")
        if (!isSupported(TuningFeature.MIC_GAIN)) return@withContext unsupported(TuningFeature.MIC_GAIN)
        applyKernelWrites(
            successLabel = "Microphone analog gain set to level $clamped",
            KernelNodes.MIC_GAIN_PROC to clamped.toString()
        )
    }

    suspend fun setActiveMic(mode: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = mode.coerceIn(0, 3)
        prefs.edit().putInt("active_mic_mode", clamped).apply()
        val label = when (clamped) {
            0 -> "Main Mic (Bottom AIN0)"
            1 -> "Top Mic (Secondary AIN2)"
            2 -> "Headset Mic (3.5mm AIN1)"
            3 -> "Dual Mic (Stereo AIN0+AIN2)"
            else -> "Main Mic"
        }
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Active microphone")
        if (!isSupported(TuningFeature.ACTIVE_MIC)) return@withContext unsupported(TuningFeature.ACTIVE_MIC)

        val kernelResult = applyKernelWrites(
            successLabel = "Active microphone set to $label",
            KernelNodes.ACTIVE_MIC_PROC to clamped.toString(),
            KernelNodes.ACTIVE_MIC_SYS to clamped.toString()
        )

        // Companion ALSA mixer routing for immediate audio session redirection
        val mixerCmd = when (clamped) {
            0 -> "tinymix 'PGA L Select' 'AIN0' 2>/dev/null; tinymix 'PGA R Select' 'None' 2>/dev/null"
            1 -> "tinymix 'PGA L Select' 'None' 2>/dev/null; tinymix 'PGA R Select' 'AIN2' 2>/dev/null"
            2 -> "tinymix 'PGA L Select' 'AIN1' 2>/dev/null; tinymix 'PGA R Select' 'None' 2>/dev/null"
            3 -> "tinymix 'PGA L Select' 'AIN0' 2>/dev/null; tinymix 'PGA R Select' 'AIN2' 2>/dev/null"
            else -> ""
        }
        if (mixerCmd.isNotEmpty()) {
            ShellUtils.runCommand(mixerCmd)
        }
        kernelResult
    }

    suspend fun setWakelockBlocker(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("wakelock_blocker", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Wakelock blocker")
        if (!isSupported(TuningFeature.WAKELOCK_BLOCKER)) return@withContext unsupported(TuningFeature.WAKELOCK_BLOCKER)
        applyKernelWrites(
            successLabel = if (enable) "Aggressive deep sleep wakelock blocker active" else "Wakelock blocker disabled",
            KernelNodes.WAKELOCK_BLOCKER_PROC to if (enable) "1" else "0"
        )
    }

    suspend fun setVibratorStrength(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt("vibrator_strength", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Vibrator strength")
        if (!isSupported(TuningFeature.VIBRATOR_STRENGTH)) return@withContext unsupported(TuningFeature.VIBRATOR_STRENGTH)
        val level = (clamped * 13 / 100).coerceIn(0, 13)
        applyKernelWrites(
            successLabel = "Vibrator strength set to $clamped% (level=0x${Integer.toHexString(level).uppercase()})",
            KernelNodes.VIBRATOR_STRENGTH_PROC to level.toString(),
            KernelNodes.VIBRATOR_VMAX to level.toString()
        )
    }

    // ---- Media & Camera Studio ----

    suspend fun setCamera4k60(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("camera_4k60", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Camera 4K60 FPS")
        if (!isSupported(TuningFeature.CAMERA_4K60)) return@withContext unsupported(TuningFeature.CAMERA_4K60)
        applyKernelWrites(
            successLabel = if (enable) "Camera 4K60 FPS mode unlocked" else "Standard camera 30 FPS cap restored",
            KernelNodes.CAMERA_4K60_PROC to if (enable) "1" else "0",
            KernelNodes.CAMERA_4K60_SYS to if (enable) "1" else "0"
        )
    }

    suspend fun setSlog3(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("camera_slog3", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Sony S-Log3")
        if (!isSupported(TuningFeature.CAMERA_SLOG3)) return@withContext unsupported(TuningFeature.CAMERA_SLOG3)
        applyKernelWrites(
            successLabel = if (enable) "Sony S-Log3 Cinema Flat gamma active" else "Standard camera ISP curve restored",
            KernelNodes.CAMERA_SLOG3_PROC to if (enable) "1" else "0",
            KernelNodes.CAMERA_SLOG3_SYS to if (enable) "1" else "0"
        )
    }

    suspend fun setTorchBrightness(level: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = level.coerceIn(0, 10)
        prefs.edit().putInt("torch_brightness", clamped).apply()
        // Try unified bridge first (works rootlessly as well since /proc/perfmgr/torch_brightness & pox_config are 0666)
        if (PoxConfigBridge.writeConfig("torch_brightness", clamped.toString())) {
            return@withContext TuningApplyResult(
                true,
                if (clamped == 0) "BegoTorch turned off" else "BegoTorch set to Level $clamped/10"
            )
        }
        applyKernelWrites(
            successLabel = if (clamped == 0) "BegoTorch turned off" else "BegoTorch set to Level $clamped/10",
            KernelNodes.TORCH_BRIGHTNESS_PROC to clamped.toString(),
            KernelNodes.TORCH_BRIGHTNESS_SYS to clamped.toString()
        )
    }

    // ---- Network & Tethering ----

    suspend fun setTcpCongestion(algorithm: String): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putString("tcp_congestion", algorithm).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("TCP congestion control")
        if (!isSupported(TuningFeature.TCP_CONGESTION)) return@withContext unsupported(TuningFeature.TCP_CONGESTION)
        applyKernelWrites(
            successLabel = "TCP Congestion algorithm set to $algorithm",
            KernelNodes.TCP_CONGESTION_CONTROL to algorithm
        )
    }

    suspend fun setTtl64(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("ttl_64", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Tethering TTL 64 uncap")
        if (!isSupported(TuningFeature.TTL_64)) return@withContext unsupported(TuningFeature.TTL_64)

        val cmd = if (enable) {
            "iptables -t mangle -C POSTROUTING -j TTL --ttl-set 64 2>/dev/null || iptables -t mangle -A POSTROUTING -j TTL --ttl-set 64; " +
            "ip6tables -t mangle -C POSTROUTING -j HL --hl-set 64 2>/dev/null || ip6tables -t mangle -A POSTROUTING -j HL --hl-set 64"
        } else {
            "iptables -t mangle -D POSTROUTING -j TTL --ttl-set 64 2>/dev/null; " +
            "ip6tables -t mangle -D POSTROUTING -j HL --hl-set 64 2>/dev/null"
        }
        val res = ShellUtils.runCommand(cmd)
        if (res.isSuccess) {
            TuningApplyResult(true, if (enable) "Tethering TTL 64 / HL 64 uncap active" else "Tethering TTL uncap removed")
        } else {
            TuningApplyResult(false, "Failed to apply iptables TTL rule: ${res.output.joinToString()}")
        }
    }

    // ---- Advanced Kernel Scheduler & Power ----

    suspend fun setWqPowerEfficient(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("wq_power_efficient", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Workqueue power efficiency")
        if (!isSupported(TuningFeature.WORKQUEUE_POWER_EFFICIENT)) return@withContext unsupported(TuningFeature.WORKQUEUE_POWER_EFFICIENT)
        applyKernelWrites(
            successLabel = if (enable) "Workqueue power-efficient unbound clustering enabled" else "Performance affinity workqueues enabled",
            KernelNodes.WQ_POWER_EFFICIENT to if (enable) "Y" else "N"
        )
    }

    suspend fun setWaltCpuUtil(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("walt_cpu_util", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("WALT CPU utilization")
        if (!isSupported(TuningFeature.WALT_CPU_UTIL)) return@withContext unsupported(TuningFeature.WALT_CPU_UTIL)
        applyKernelWrites(
            successLabel = if (enable) "WALT CPU utilization tracking active" else "Pel-based CPU utilization active",
            KernelNodes.SCHED_WALT_CPU_UTIL to if (enable) "1" else "0"
        )
    }

    suspend fun setWaltTaskUtil(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("walt_task_util", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("WALT task utilization")
        if (!isSupported(TuningFeature.WALT_TASK_UTIL)) return@withContext unsupported(TuningFeature.WALT_TASK_UTIL)
        applyKernelWrites(
            successLabel = if (enable) "WALT task utilization tracking active" else "Pel-based task utilization active",
            KernelNodes.SCHED_WALT_TASK_UTIL to if (enable) "1" else "0"
        )
    }

    suspend fun setSchedCstateAware(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("sched_cstate_aware", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("C-State aware scheduling")
        if (!isSupported(TuningFeature.SCHED_CSTATE_AWARE)) return@withContext unsupported(TuningFeature.SCHED_CSTATE_AWARE)
        applyKernelWrites(
            successLabel = if (enable) "C-State wake energy minimization active" else "C-State aware scheduling disabled",
            KernelNodes.SCHED_CSTATE_AWARE to if (enable) "1" else "0"
        )
    }

    // ---- Memory Tuning ----

    suspend fun setVmSwappiness(swappiness: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = swappiness.coerceIn(0, 200)
        prefs.edit().putInt("vm_swappiness", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("VM swappiness")
        if (!isSupported(TuningFeature.VM_SWAPPINESS)) return@withContext unsupported(TuningFeature.VM_SWAPPINESS)
        applyKernelWrites(
            successLabel = "VM swappiness set to $clamped",
            KernelNodes.VM_SWAPPINESS to clamped.toString()
        )
    }

    // ---- Storage ----

    suspend fun setDynamicFsync(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dynamic_fsync", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Dynamic Fsync")
        if (!isSupported(TuningFeature.DYNAMIC_FSYNC)) return@withContext unsupported(TuningFeature.DYNAMIC_FSYNC)
        applyKernelWrites(
            successLabel = if (enable) "Dynamic Fsync active" else "Dynamic Fsync disabled",
            KernelNodes.DYNAMIC_FSYNC_PROC to if (enable) "1" else "0"
        )
    }

    suspend fun setDriveDroidCdrom(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("drivedroid_cdrom", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("DriveDroid CD-ROM")
        if (!isSupported(TuningFeature.DRIVEDROID)) return@withContext unsupported(TuningFeature.DRIVEDROID)
        val v = if (enable) "1" else "0"
        applyKernelWrites(
            successLabel = if (enable) "DriveDroid USB CD-ROM emulation enabled" else "DriveDroid USB CD-ROM emulation disabled",
            KernelNodes.DRIVEDROID_CDROM_PROC to v,
            KernelNodes.DRIVEDROID_CDROM_SYS to v,
            KernelNodes.DRIVEDROID_LUN0_SYS to v
        )
    }

    suspend fun setReadAheadKb(kb: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("read_ahead_kb", kb).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Readahead buffer")
        val queues = blockQueues()
        if (queues.isEmpty()) {
            return@withContext TuningApplyResult(false, "No writable block queue found under /sys/block")
        }
        applyKernelWrites(
            successLabel = "Readahead buffer set to ${kb}KB on ${queues.size} queue(s)",
            *queues.map { "$it/read_ahead_kb" to kb.toString() }.toTypedArray()
        )
    }

    /** Block queues that actually exist, discovered instead of guessed. */
    private suspend fun blockQueues(): List<String> =
        KernelNodes.blockDevices().map { "$it/queue" }

    private suspend fun notApplied(action: String): TuningApplyResult =
        TuningApplyResult(false, "$action saved (root required to apply to kernel directly)")

    // ---- Standard Linux controls: these work on every root solution ----

    /**
     * Switch the I/O scheduler.
     *
     * `queue/scheduler` expects the whole option list back with the new entry
     * bracketed, so a bare scheduler name is rejected. begonia offers noop,
     * deadline and cfq, plus the mq variants kyber and deadline-mq depending on
     * the queue.
     */
    suspend fun setIoScheduler(scheduler: String): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("I/O scheduler")
        if (!isSupported(TuningFeature.IO_SCHEDULER)) return@withContext unsupported(TuningFeature.IO_SCHEDULER)

        var applied = 0
        val failures = mutableListOf<String>()
        for (queue in blockQueues()) {
            val raw = ShellUtils.readNode("$queue/scheduler") ?: continue
            val (active, options) = KernelNodes.parseSelection(raw)
            if (active == scheduler) { applied++; continue }
            val payload = KernelNodes.renderSelection(options, scheduler)
            if (payload == null) { failures += queue; continue }
            if (ShellUtils.fastWrite("$queue/scheduler", payload)) applied++ else failures += queue
        }

        when {
            applied > 0 && failures.isEmpty() -> TuningApplyResult(true, "I/O scheduler set to $scheduler")
            applied > 0 -> TuningApplyResult(true, "I/O scheduler set to $scheduler (skipped ${failures.size} queue(s))")
            else -> TuningApplyResult(false, "Kernel rejected $scheduler on ${failures.size} queue(s)")
        }
    }

    /**
     * Set the cpufreq governor on every policy.
     *
     * begonia compiles schedutil, performance, powersave, ondemand and userspace.
     */
    suspend fun setCpuGovernor(governor: String): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("CPU governor")
        if (!isSupported(TuningFeature.CPU_GOVERNOR)) return@withContext unsupported(TuningFeature.CPU_GOVERNOR)

        val policies = KernelNodes.cpufreqPolicies()
        if (policies.isEmpty()) return@withContext TuningApplyResult(false, "No cpufreq policy found")

        var applied = 0
        for (policy in policies) {
            if (ShellUtils.fastWrite("$policy/cpufreq/scaling_governor", governor)) applied++
        }

        if (applied == 0) {
            TuningApplyResult(false, "No policy accepted '$governor'")
        } else {
            TuningApplyResult(true, "Governor '$governor' on $applied/${policies.size} policies")
        }
    }

    /** Resize the zram backing device. Takes effect immediately. */
    suspend fun setZramSizeMb(sizeMb: Long): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("ZRAM size")
        if (!isSupported(TuningFeature.ZRAM_SIZE)) return@withContext unsupported(TuningFeature.ZRAM_SIZE)

        val bytes = sizeMb.coerceAtLeast(64) * 1024 * 1024
        val ok = ShellUtils.fastWrite("/sys/block/zram0/disksize", bytes.toString()) {
            it.trim().toLongOrNull() == bytes
        }
        if (ok) TuningApplyResult(true, "ZRAM set to ${sizeMb}MB") else TuningApplyResult(false, "Kernel rejected ${sizeMb}MB (try 64/128/256/512)")
    }

    /** Cap the frame rate using MediaTek's syslimiter. */
    suspend fun setFpsCap(fps: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("FPS cap")
        if (!isSupported(TuningFeature.FPS_CAP)) return@withContext unsupported(TuningFeature.FPS_CAP)

        if (fps == 0) {
            // 0 means uncapped: clear the limiter.
            return@withContext applyKernelWrites(
                successLabel = "FPS cap released",
                KernelNodes.FPS_FORCE_DISABLE to "1"
            )
        }

        val megahertz = fps * 1000
        val nodes = mapOf(
            60 to KernelNodes.FPS_60,
            90 to KernelNodes.FPS_90,
            120 to KernelNodes.FPS_120,
            144 to KernelNodes.FPS_144
        )
        val target = nodes[fps]
            ?: return@withContext TuningApplyResult(false, "This kernel exposes 60/90/120/144 only")

        val cleared = applyKernelWrites(
            successLabel = "FPS cap cleared on the other refresh rates",
            *nodes.filterValues { it != target }.values.map { it to "0" }.toTypedArray()
        )
        val applied = applyKernelWrites(
            successLabel = "FPS capped at $fps",
            KernelNodes.FPS_FORCE_DISABLE to "0",
            target to megahertz.toString()
        )

        if (applied.isHardwareApplied) applied
        else TuningApplyResult(cleared.isHardwareApplied, applied.message)
    }

    fun getActiveTuningMode(): String {
        return prefs.getString("active_tuning_mode", "BRAINLESS") ?: "BRAINLESS"
    }

    fun setActiveTuningMode(mode: String) {
        prefs.edit().putString("active_tuning_mode", mode).apply()
    }

    fun isAdvancedCustomized(): Boolean = prefs.getBoolean("advanced_customized", false)

    fun markAdvancedCustomized(customized: Boolean) {
        prefs.edit().putBoolean("advanced_customized", customized).apply()
    }

    fun getActiveBoundPresetTitle(): String {
        val lastBound = prefs.getString("last_bound_preset", null)
        if (!lastBound.isNullOrBlank()) return lastBound
        val mode = getActiveTuningMode()
        return when (mode) {
            "BRAINLESS" -> "Brainless • ${getActiveBrainlessProfile().title}"
            "EASY" -> "Easy Mode • ${getActiveEasyTier().title} (${getActiveEasyProfile().title})"
            else -> "Brainless • ${getActiveBrainlessProfile().title}"
        }
    }

    suspend fun reapplyBoundPreset(): TuningApplyResult {
        val mode = getActiveTuningMode()
        return if (mode == "EASY") {
            val tier = getActiveEasyTier()
            val profile = getActiveEasyProfile()
            val preset = EasyModePresets.getPreset(tier, profile)
            applyEasyPreset(preset)
        } else {
            val profile = getActiveBrainlessProfile()
            applyBrainlessProfile(profile)
        }
    }

    fun getActiveEasyTier(): EasyModeTier {
        val name = prefs.getString("active_easy_tier", EasyModeTier.NORMAL.name)
        return try {
            EasyModeTier.valueOf(name ?: EasyModeTier.NORMAL.name)
        } catch (e: Exception) {
            EasyModeTier.NORMAL
        }
    }

    fun getActiveEasyProfile(): EasyModeProfile {
        val name = prefs.getString("active_easy_profile", EasyModeProfile.BALANCED.name)
        return try {
            EasyModeProfile.valueOf(name ?: EasyModeProfile.BALANCED.name)
        } catch (e: Exception) {
            EasyModeProfile.BALANCED
        }
    }

    fun getActiveBrainlessProfile(): BrainlessProfile {
        val name = prefs.getString("active_brainless_profile", BrainlessProfile.STABILITY.name)
        return try {
            BrainlessProfile.valueOf(name ?: BrainlessProfile.STABILITY.name)
        } catch (_: Exception) {
            BrainlessProfile.STABILITY
        }
    }

    suspend fun applyBrainlessProfile(profile: BrainlessProfile): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Brainless: ${profile.title}")

        isApplyingPreset = true
        try {
            AdaptiveLearningManager.syncWithKernelTelemetry()
            val stats = AdaptiveLearningManager.getStats()
            val base = BrainlessPresets.getPreset(profile)
            val adapted = BrainlessPresets.adaptPreset(base, stats)

            prefs.edit()
                .putString("active_tuning_mode", "BRAINLESS")
                .putString("active_brainless_profile", profile.name)
                .putString("last_bound_preset", "Brainless • ${profile.title}")
                .putBoolean("advanced_customized", false)
                .apply()

            val gMode = when (adapted.effectiveGamingMode) {
                -1 -> GamingModeLevel.POWERSAVE
                1 -> GamingModeLevel.GAMING
                2 -> GamingModeLevel.EXTREME
                else -> GamingModeLevel.BALANCED
            }
            val kpMode = when (adapted.effectiveKprofileLevel) {
                1 -> KprofilesLevel.BATTERY
                2 -> KprofilesLevel.BALANCED
                3 -> KprofilesLevel.PERFORMANCE
                else -> KprofilesLevel.BALANCED
            }
            val healthMode = when (adapted.effectiveHealthCalibration) {
                1 -> HealthCalibrationMode.AUTO_ADAPTIVE
                2 -> HealthCalibrationMode.PERF_GUARD
                3 -> HealthCalibrationMode.LONGEVITY
                else -> HealthCalibrationMode.AUTO_ADAPTIVE
            }

            setGamingMode(gMode)
            setKprofilesMode(kpMode)
            setSchedBore(adapted.effectiveSchedBoreEnabled)
            setSchedBurstPenaltyScale(adapted.effectiveBurstPenaltyScale)
            setSchedBurstPenaltyOffset(adapted.effectiveBurstPenaltyOffset)
            setSchedBurstInheritType(adapted.effectiveBurstInheritType)
            setTouchBoost(adapted.effectiveTouchGameMode)
            setTouchSensitivity(adapted.effectiveTouchSensitivity)
            setFastCharge(adapted.effectiveFastChargeEnabled)
            setChargeWatt(adapted.effectiveChargeWatt)
            setDynamicFsync(adapted.effectiveDynamicFsync)
            setWakelockBlocker(adapted.effectiveWakelockBlocker)
            setTrueTone(adapted.effectiveTrueTone)
            setHealthCalibrationMode(healthMode)

            // Apply physical silicon calibration limits
            setBatteryEsr(stats.batteryEsrMohm)
            setOppSagCeiling(adapted.effectiveOppCeilingKhz)
            setThermalGradient(stats.thermalRiseRateMcs)
            setOppVoltageGuard(true)

            val adaptiveSummary = if (adapted.isAdapted) " • Adapted to your battery & silicon" else ""
            TuningApplyResult(
                isHardwareApplied = true,
                message = "Activated & bound ${profile.title}$adaptiveSummary"
            )
        } finally {
            isApplyingPreset = false
        }
    }

    suspend fun applyEasyPreset(config: EasyPresetConfig): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Preset: ${config.tier.title} - ${config.profile.title}")

        isApplyingPreset = true
        try {
            // 1. Sync real-time feedback from kernel
            AdaptiveLearningManager.syncWithKernelTelemetry()
            val stats = AdaptiveLearningManager.getStats()
            val adapted = EasyModePresets.adaptPreset(config, stats)

            prefs.edit()
                .putString("active_tuning_mode", "EASY")
                .putString("active_easy_tier", config.tier.name)
                .putString("active_easy_profile", config.profile.name)
                .putString("last_bound_preset", "Easy Mode • ${config.tier.title} (${config.profile.title})")
                .putBoolean("advanced_customized", false)
                .apply()

            val gMode = when (adapted.effectiveGamingMode) {
                -1 -> GamingModeLevel.POWERSAVE
                1 -> GamingModeLevel.GAMING
                2 -> GamingModeLevel.EXTREME
                else -> GamingModeLevel.BALANCED
            }
            val kpMode = when (adapted.effectiveKprofileLevel) {
                1 -> KprofilesLevel.BATTERY
                2 -> KprofilesLevel.BALANCED
                3 -> KprofilesLevel.PERFORMANCE
                else -> KprofilesLevel.BALANCED
            }
            val healthMode = when (adapted.effectiveHealthCalibration) {
                1 -> HealthCalibrationMode.AUTO_ADAPTIVE
                2 -> HealthCalibrationMode.PERF_GUARD
                3 -> HealthCalibrationMode.LONGEVITY
                else -> HealthCalibrationMode.AUTO_ADAPTIVE
            }

            setGamingMode(gMode)
            setKprofilesMode(kpMode)
            setSchedBore(adapted.effectiveSchedBoreEnabled)
            setSchedBurstPenaltyScale(adapted.effectiveBurstPenaltyScale)
            setSchedBurstPenaltyOffset(adapted.effectiveBurstPenaltyOffset)
            setSchedBurstInheritType(adapted.effectiveBurstInheritType)
            setTouchBoost(adapted.effectiveTouchGameMode)
            setTouchSensitivity(adapted.effectiveTouchSensitivity)
            setFastCharge(adapted.effectiveFastChargeEnabled)
            setChargeWatt(adapted.effectiveChargeWatt)
            setDynamicFsync(adapted.effectiveDynamicFsync)
            setWakelockBlocker(adapted.effectiveWakelockBlocker)
            setTrueTone(adapted.effectiveTrueTone)
            setHealthCalibrationMode(healthMode)

            // Apply physical silicon calibration limits
            setBatteryEsr(stats.batteryEsrMohm)
            setOppSagCeiling(adapted.effectiveOppCeilingKhz)
            setThermalGradient(stats.thermalRiseRateMcs)
            setOppVoltageGuard(true)

            val adaptiveSummary = if (adapted.isAdapted) " • Adapted to your silicon & battery" else ""
            TuningApplyResult(
                isHardwareApplied = true,
                message = "Applied & bound ${config.tier.title} (${config.profile.title})$adaptiveSummary"
            )
        } finally {
            isApplyingPreset = false
        }
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