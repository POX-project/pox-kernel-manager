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
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/proc/perfmgr/gaming_mode", level.value.toString())
            if (level == GamingModeLevel.EXTREME) {
                ShellUtils.fastWrite("/proc/perfmgr/tchbst/touch_boost", "1")
                ShellUtils.runCommand("setprop persist.sys.power_mode_perf 1")
            } else if (level == GamingModeLevel.BALANCED) {
                ShellUtils.runCommand("setprop persist.sys.power_mode_perf 0")
            }
            TuningApplyResult(true, "${level.title} applied to kernel")
        } else {
            TuningApplyResult(false, "${level.title} saved (Grant root to apply to kernel directly)")
        }
    }

    suspend fun setBypassCharging(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("bypass_charging", enable).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/sys/kernel/battery_protection/bypass_mode", if (enable) "1" else "0")
            TuningApplyResult(true, if (enable) "Direct-Power Bypass Enabled" else "Normal Battery Charging")
        } else {
            TuningApplyResult(false, "Bypass preference saved (Root required for direct hardware bypass)")
        }
    }

    suspend fun setChargeLimit(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(60, 100)
        prefs.edit().putInt("charge_limit", clamped).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/sys/kernel/battery_protection/charge_limit", clamped.toString())
            TuningApplyResult(true, "Charge limit set to $clamped%")
        } else {
            TuningApplyResult(false, "Charge limit set to $clamped% (Saved locally)")
        }
    }

    suspend fun setThermalGuard(enable: Boolean, tempLimit: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("thermal_guard", enable).putInt("temp_limit", tempLimit).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/sys/kernel/battery_protection/thermal_guard", if (enable) "1" else "0")
            ShellUtils.fastWrite("/sys/kernel/battery_protection/temp_limit", tempLimit.toString())
            TuningApplyResult(true, "Thermal Guard set to $tempLimit°C")
        } else {
            TuningApplyResult(false, "Thermal Guard saved ($tempLimit°C)")
        }
    }

    suspend fun setXswapdEnabled(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("xswapd_enable", enable).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/dev/memcg/memory.xswapd.enable", if (enable) "1" else "0")
            TuningApplyResult(true, if (enable) "xswapd daemon enabled" else "xswapd daemon disabled")
        } else {
            TuningApplyResult(false, "xswapd preference saved locally")
        }
    }

    suspend fun triggerXswapdReclaim(): TuningApplyResult = withContext(Dispatchers.IO) {
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/dev/memcg/memory.xswapd.reclaim", "104857600")
            TuningApplyResult(true, "Triggered 100MB xswapd memory reclaim")
        } else {
            // Unrooted trigger: run trim memory / gc request
            System.gc()
            TuningApplyResult(false, "Triggered app-level memory clean (Root required for kernel xswapd reclaim)")
        }
    }

    suspend fun setDoubleTapToWake(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dt2w", enable).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            val v = if (enable) "1" else "0"
            ShellUtils.fastWrite("/proc/perfmgr/dt2w", v)
            ShellUtils.fastWrite("/sys/android_touch/double_click", v)
            TuningApplyResult(true, if (enable) "DT2W Enabled" else "DT2W Disabled")
        } else {
            TuningApplyResult(false, "DT2W preference saved locally")
        }
    }

    suspend fun setTrueTone(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("true_tone", enable).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/proc/perfmgr/true_tone", if (enable) "1" else "0")
            TuningApplyResult(true, if (enable) "True Tone D65 Calibrated" else "True Tone Disabled")
        } else {
            TuningApplyResult(false, "True Tone preference saved locally")
        }
    }

    suspend fun setVibratorStrength(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt("vibrator_strength", clamped).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/proc/perfmgr/vibrator_strength", clamped.toString())
            TuningApplyResult(true, "Vibrator strength set to $clamped%")
        } else {
            TuningApplyResult(false, "Vibrator strength set to $clamped% (Saved locally)")
        }
    }

    suspend fun setDynamicFsync(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dynamic_fsync", enable).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/sys/kernel/dyn_fsync/Dyn_fsync_active", if (enable) "1" else "0")
            TuningApplyResult(true, if (enable) "Dynamic Fsync Active" else "Dynamic Fsync Off")
        } else {
            TuningApplyResult(false, "Dynamic Fsync preference saved locally")
        }
    }

    suspend fun setReadAheadKb(kb: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putInt("read_ahead_kb", kb).apply()
        val isRoot = ShellUtils.isRootAvailable()
        if (isRoot) {
            ShellUtils.fastWrite("/sys/block/sda/queue/read_ahead_kb", kb.toString())
            ShellUtils.fastWrite("/sys/block/sdb/queue/read_ahead_kb", kb.toString())
            ShellUtils.fastWrite("/sys/block/sdc/queue/read_ahead_kb", kb.toString())
            TuningApplyResult(true, "Readahead buffer set to ${kb}KB")
        } else {
            TuningApplyResult(false, "Readahead buffer set to ${kb}KB (Saved locally)")
        }
    }
}
