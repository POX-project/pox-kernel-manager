package com.pox.kernelmanager.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.TuningFeatures
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TuningApplyResult(
    val isHardwareApplied: Boolean,
    val message: String
)

object TuningRepository {

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences("pox_tuning_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Write a set of kernel nodes and report what actually happened.
     *
     * [writes] is a list of path/value pairs; the call succeeds when at least one of
     * them lands, because POX exposes several of these features under two nodes and
     * any single kernel build implements only one of them.
     *
     * Every setter used to ignore `fastWrite` and return success unconditionally,
     * so a missing `/proc/perfmgr` node was indistinguishable from a working write.
     */
    private suspend fun applyKernelWrites(
        successLabel: String,
        vararg writes: Pair<String, String>
    ): TuningApplyResult {
        val accepted = writes.filter { ShellUtils.fastWrite(it.first, it.second) }.map { it.first }.toSet()
        val rejected = writes.map { it.first }.filterNot { it in accepted }

        return when {
            rejected.isEmpty() -> TuningApplyResult(true, successLabel)
            accepted.isNotEmpty() -> TuningApplyResult(
                true,
                "$successLabel (this kernel does not expose ${rejected.joinToString()})"
            )
            else -> TuningApplyResult(
                false,
                "Kernel rejected the write - none of ${writes.joinToString { it.first }} are writable"
            )
        }
    }

    private suspend fun notApplied(action: String): TuningApplyResult =
        TuningApplyResult(false, "$action saved (root required to apply to kernel directly)")

    suspend fun getTuningFeatures(): TuningFeatures = withContext(Dispatchers.IO) {
        val isRoot = ShellUtils.isRootAvailable()

        // 1. Gaming Mode (Hardware read or saved preference)
        val savedGaming = prefs.getInt("gaming_mode", 0)
        val gamingRaw = if (isRoot) {
            ShellUtils.fastRead("/proc/perfmgr/gaming_mode")?.toIntOrNull() ?: savedGaming
        } else savedGaming

        val gamingLevel = when (gamingRaw) {
            1 -> GamingModeLevel.GAMING
            2 -> GamingModeLevel.EXTREME
            else -> GamingModeLevel.BALANCED
        }
        val touchBoost = gamingLevel != GamingModeLevel.BALANCED
        val fpsgoRescue = true

        // 2. Battery Guard
        val savedBypass = prefs.getBoolean("bypass_charging", false)
        val bypass = if (isRoot) {
            (ShellUtils.fastRead("/sys/kernel/battery_protection/bypass_mode") == "1")
        } else savedBypass

        val savedChargeLimit = prefs.getInt("charge_limit", 100)
        val chargeLimit = if (isRoot) {
            ShellUtils.fastRead("/sys/kernel/battery_protection/charge_limit")?.toIntOrNull() ?: savedChargeLimit
        } else savedChargeLimit

        val savedThermalGuard = prefs.getBoolean("thermal_guard", true)
        val thermalGuard = if (isRoot) {
            (ShellUtils.fastRead("/sys/kernel/battery_protection/thermal_guard") == "1")
        } else savedThermalGuard

        val savedTempLimit = prefs.getInt("temp_limit", 48)
        val tempLimit = if (isRoot) {
            ShellUtils.fastRead("/sys/kernel/battery_protection/temp_limit")?.toIntOrNull() ?: savedTempLimit
        } else savedTempLimit

        // 3. HyperOS xswapd
        val savedXswapd = prefs.getBoolean("xswapd_enable", true)
        val xswapdEnable = if (isRoot) {
            ShellUtils.fastRead("/dev/memcg/memory.xswapd.enable") != "0"
        } else savedXswapd

        val savedXswapdQuota = prefs.getLong("xswapd_quota", 1024L)
        val xswapdQuota = if (isRoot) {
            (ShellUtils.fastRead("/dev/memcg/memory.xswapd.quota")?.toLongOrNull() ?: (savedXswapdQuota * 1024 * 1024)) / (1024 * 1024)
        } else savedXswapdQuota

        // 4. Display, Touch & Vibrator
        val savedDt2w = prefs.getBoolean("dt2w", true)
        val dt2w = if (isRoot) {
            ShellUtils.fastRead("/proc/perfmgr/dt2w") == "1" ||
                    ShellUtils.fastRead("/sys/android_touch/double_click") == "1"
        } else savedDt2w

        val savedTrueTone = prefs.getBoolean("true_tone", true)
        val trueTone = if (isRoot) {
            ShellUtils.fastRead("/proc/perfmgr/true_tone") == "1"
        } else savedTrueTone

        val savedVibrator = prefs.getInt("vibrator_strength", 85)
        val vibrator = if (isRoot) {
            ShellUtils.fastRead("/proc/perfmgr/vibrator_strength")?.toIntOrNull() ?: savedVibrator
        } else savedVibrator

        // 5. Storage & Fsync
        val savedFsync = prefs.getBoolean("dynamic_fsync", true)
        val fsync = if (isRoot) {
            ShellUtils.fastRead("/sys/kernel/dyn_fsync/Dyn_fsync_active") == "1" ||
                    ShellUtils.fastRead("/sys/module/sync/parameters/fsync_enabled") != "N"
        } else savedFsync

        val savedReadAhead = prefs.getInt("read_ahead_kb", 256)
        val readAhead = if (isRoot) {
            ShellUtils.fastRead("/sys/block/sda/queue/read_ahead_kb")?.toIntOrNull() ?: savedReadAhead
        } else savedReadAhead

        val schedulerRaw = ShellUtils.fastRead("/sys/block/sda/queue/scheduler") ?: "mq-deadline"
        val activeScheduler = schedulerRaw.substringAfter("[").substringBefore("]").ifEmpty { "mq-deadline" }

        TuningFeatures(
            gamingMode = gamingLevel,
            touchBoost240Hz = touchBoost,
            fpsgoUltraRescue = fpsgoRescue,
            bypassCharging = bypass,
            chargeLimitPercent = chargeLimit,
            thermalGuardEnabled = thermalGuard,
            tempLimitC = tempLimit,
            xswapdEnabled = xswapdEnable,
            xswapdQuotaMb = xswapdQuota,
            doubleTapToWake = dt2w,
            trueToneDisplay = trueTone,
            vibratorStrengthPercent = vibrator,
            dynamicFsync = fsync,
            readAheadKb = readAhead,
            ioScheduler = activeScheduler
        )
    }

    suspend fun setGamingMode(level: GamingModeLevel): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("gaming_mode", level.value).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied(level.title)

        val result = applyKernelWrites(
            successLabel = "${level.title} applied to kernel",
            "/proc/perfmgr/gaming_mode" to level.value.toString()
        )

        if (result.isHardwareApplied) {
            when (level) {
                GamingModeLevel.EXTREME -> {
                    ShellUtils.fastWrite("/proc/perfmgr/tchbst/touch_boost", "1")
                    ShellUtils.runCommand("setprop persist.sys.power_mode_perf 1")
                }
                GamingModeLevel.BALANCED -> ShellUtils.runCommand("setprop persist.sys.power_mode_perf 0")
                GamingModeLevel.GAMING -> Unit
            }
        }
        result
    }

    suspend fun setBypassCharging(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("bypass_charging", enable).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Bypass preference saved (root required for direct hardware bypass)")
        }
        applyKernelWrites(
            successLabel = if (enable) "Direct-Power Bypass Enabled" else "Normal Battery Charging",
            "/sys/kernel/battery_protection/bypass_mode" to if (enable) "1" else "0"
        )
    }

    suspend fun setChargeLimit(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(60, 100)
        prefs.edit().putInt("charge_limit", clamped).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Charge limit set to $clamped% (saved locally)")
        }
        applyKernelWrites(
            successLabel = "Charge limit set to $clamped%",
            "/sys/kernel/battery_protection/charge_limit" to clamped.toString()
        )
    }

    suspend fun setThermalGuard(enable: Boolean, tempLimit: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("thermal_guard", enable).putInt("temp_limit", tempLimit).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Thermal Guard saved ($tempLimit°C)")
        }
        applyKernelWrites(
            successLabel = "Thermal Guard set to $tempLimit°C",
            "/sys/kernel/battery_protection/thermal_guard" to if (enable) "1" else "0",
            "/sys/kernel/battery_protection/temp_limit" to tempLimit.toString()
        )
    }

    suspend fun setXswapdEnabled(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("xswapd_enable", enable).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "xswapd preference saved locally")
        }
        applyKernelWrites(
            successLabel = if (enable) "xswapd daemon enabled" else "xswapd daemon disabled",
            "/dev/memcg/memory.xswapd.enable" to if (enable) "1" else "0"
        )
    }

    suspend fun triggerXswapdReclaim(): TuningApplyResult = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) {
            System.gc()
            return@withContext TuningApplyResult(false, "Triggered app-level memory clean (root required for kernel xswapd reclaim)")
        }
        applyKernelWrites(
            successLabel = "Triggered 100MB xswapd memory reclaim",
            "/dev/memcg/memory.xswapd.reclaim" to "104857600"
        )
    }

    suspend fun setDoubleTapToWake(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dt2w", enable).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "DT2W preference saved locally")
        }
        val v = if (enable) "1" else "0"
        applyKernelWrites(
            successLabel = if (enable) "DT2W Enabled" else "DT2W Disabled",
            "/proc/perfmgr/dt2w" to v,
            "/sys/android_touch/double_click" to v
        )
    }

    suspend fun setTrueTone(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("true_tone", enable).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "True Tone preference saved locally")
        }
        applyKernelWrites(
            successLabel = if (enable) "True Tone D65 Calibrated" else "True Tone Disabled",
            "/proc/perfmgr/true_tone" to if (enable) "1" else "0"
        )
    }

    suspend fun setVibratorStrength(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt("vibrator_strength", clamped).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Vibrator strength set to $clamped% (saved locally)")
        }
        applyKernelWrites(
            successLabel = "Vibrator strength set to $clamped%",
            "/proc/perfmgr/vibrator_strength" to clamped.toString()
        )
    }

    suspend fun setDynamicFsync(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dynamic_fsync", enable).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Dynamic Fsync preference saved locally")
        }
        applyKernelWrites(
            successLabel = if (enable) "Dynamic Fsync Active" else "Dynamic Fsync Off",
            "/sys/kernel/dyn_fsync/Dyn_fsync_active" to if (enable) "1" else "0"
        )
    }

    suspend fun setReadAheadKb(kb: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("read_ahead_kb", kb).apply()
        if (!ShellUtils.isRootAvailable()) {
            return@withContext TuningApplyResult(false, "Readahead buffer set to ${kb}KB (saved locally)")
        }
        // Block names are storage-layout specific; most are absent on begonia.
        val blocks = listOf("sda", "sdb", "sdc", "mmcblk0", "dm-0")
        applyKernelWrites(
            successLabel = "Readahead buffer set to ${kb}KB",
            *blocks.map { "/sys/block/$it/queue/read_ahead_kb" to kb.toString() }.toTypedArray()
        )
    }
}