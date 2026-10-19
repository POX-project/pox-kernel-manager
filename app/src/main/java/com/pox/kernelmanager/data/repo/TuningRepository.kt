package com.pox.kernelmanager.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.TuningFeature
import com.pox.kernelmanager.data.model.TuningFeatures
import com.pox.kernelmanager.data.root.KernelNodes
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
     * EAS boost applied per gaming level.
     *
     * `eas_ctrl.c` clamps through `MIN_BOOST_VALUE`/`MAX_BOOST_VALUE`, both -100
     * and 100, so these stay inside the range the kernel accepts.
     */
    private val boostForMode = mapOf(
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
    private suspend fun applyKernelWrites(
        successLabel: String,
        vararg writes: Pair<String, String>
    ): TuningApplyResult {
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

        val savedGaming = prefs.getInt("gaming_mode", 0)
        val gamingRaw = if (isRoot) {
            val procMode = ShellUtils.fastRead(KernelNodes.GAMING_MODE_PROC)?.trim()?.toIntOrNull()
            if (procMode != null) procMode else {
                val mode = ShellUtils.fastRead(KernelNodes.GAMING_MODE_SYS)?.trim()?.toIntOrNull()
                if (mode != null) mode else {
                    val boost = ShellUtils.readNode(KernelNodes.BOOST_FG)?.trim()?.toIntOrNull()
                    if (boost != null) {
                        boostForMode.entries.minByOrNull { kotlin.math.abs(it.value - boost) }?.key?.value
                            ?: savedGaming
                    } else savedGaming
                }
            }
        } else savedGaming

        val gamingLevel = when (gamingRaw) {
            1 -> GamingModeLevel.GAMING
            2 -> GamingModeLevel.EXTREME
            else -> GamingModeLevel.BALANCED
        }

        val touchBoost = if (isRoot) {
            ShellUtils.fastRead(KernelNodes.TOUCH_GAME_MODE_PROC) == "1" ||
            KernelNodes.parseUsrtchField(
                ShellUtils.readNode(KernelNodes.USRTCH).orEmpty(),
                "enable"
            )?.let { it == 1 } ?: false
        } else prefs.getBoolean("touch_boost", false)

        val touchSensitivity = if (isRoot && isSupported(TuningFeature.TOUCH_SENSITIVITY)) {
            ShellUtils.fastRead(KernelNodes.TOUCH_SENSITIVITY_PROC)?.toIntOrNull()
                ?: prefs.getInt("touch_sensitivity", 0)
        } else prefs.getInt("touch_sensitivity", 0)

        // Battery guard & charging controls
        val fastCharge = if (isRoot && isSupported(TuningFeature.FAST_CHARGE)) {
            ShellUtils.fastRead(KernelNodes.FAST_CHARGE_PROC) != "0"
        } else prefs.getBoolean("fast_charge", true)

        val bypass = if (isRoot && isSupported(TuningFeature.BYPASS_CHARGING)) {
            ShellUtils.fastRead(KernelNodes.BATTERY_BYPASS_PROC) == "1" ||
            ShellUtils.fastRead(KernelNodes.BATTERY_BYPASS_SYS) == "1" ||
            ShellUtils.fastRead(KernelNodes.BATTERY_CHARGING_ENABLED) == "0"
        } else prefs.getBoolean("bypass_charging", false)

        val chargeLimit = if (isRoot && isSupported(TuningFeature.CHARGE_LIMIT)) {
            ShellUtils.fastRead(KernelNodes.BATTERY_LIMIT_PROC)?.toIntOrNull()
                ?: ShellUtils.fastRead(KernelNodes.BATTERY_LIMIT_SYS)?.toIntOrNull()
                ?: ShellUtils.fastRead(KernelNodes.BATTERY_CHARGE_CONTROL_LIMIT)?.toIntOrNull()
                ?: prefs.getInt("charge_limit", 100)
        } else prefs.getInt("charge_limit", 100)

        val thermalGuard = if (isRoot && isSupported(TuningFeature.THERMAL_GUARD)) {
            ShellUtils.fastRead(KernelNodes.THERMAL_GUARD_SYS) == "1"
        } else prefs.getBoolean("thermal_guard", false)

        val tempLimit = if (isRoot && isSupported(TuningFeature.THERMAL_GUARD)) {
            ShellUtils.fastRead(KernelNodes.TEMP_LIMIT_SYS)?.toIntOrNull()
                ?: prefs.getInt("temp_limit", 48)
        } else prefs.getInt("temp_limit", 48)

        // xswapd is a MIUI/HyperOS userspace daemon.
        val xswapdEnable = if (isRoot && isSupported(TuningFeature.XSWAPD)) {
            ShellUtils.fastRead("/dev/memcg/memory.xswapd.enable") != "0"
        } else prefs.getBoolean("xswapd_enable", false)

        val savedXswapdQuota = prefs.getLong("xswapd_quota", 1024L)
        val xswapdQuota = if (isRoot && isSupported(TuningFeature.XSWAPD)) {
            (ShellUtils.fastRead("/dev/memcg/memory.xswapd.quota")?.toLongOrNull()
                ?: (savedXswapdQuota * 1024 * 1024)) / (1024 * 1024)
        } else savedXswapdQuota

        // Touch, display and haptics
        val dt2w = if (isRoot && isSupported(TuningFeature.DT2W)) {
            ShellUtils.fastRead(KernelNodes.DT2W_PROC) == "1"
        } else prefs.getBoolean("dt2w", false)

        val trueTone = if (isRoot && isSupported(TuningFeature.TRUE_TONE)) {
            ShellUtils.fastRead(KernelNodes.TRUE_TONE_SYS) == "1" ||
            ShellUtils.readNode(KernelNodes.TRUE_TONE_PROC)?.contains("enabled") == true ||
            ShellUtils.fastRead(KernelNodes.TRUE_TONE_PROC) == "1"
        } else prefs.getBoolean("true_tone", false)

        val colorMode = if (isRoot && isSupported(TuningFeature.COLOR_MODE)) {
            ShellUtils.fastRead(KernelNodes.COLOR_MODE_PROC)?.toIntOrNull()
                ?: prefs.getInt("color_mode", 0)
        } else prefs.getInt("color_mode", 0)

        val hbmMode = if (isRoot && isSupported(TuningFeature.HBM_MODE)) {
            ShellUtils.fastRead(KernelNodes.HBM_MODE_PROC)?.toIntOrNull()
                ?: prefs.getInt("hbm_mode", 0)
        } else prefs.getInt("hbm_mode", 0)

        val vibrator = if (isRoot && isSupported(TuningFeature.VIBRATOR_STRENGTH)) {
            val level = ShellUtils.fastRead(KernelNodes.VIBRATOR_STRENGTH_PROC)?.toIntOrNull()
                ?: ShellUtils.fastRead(KernelNodes.VIBRATOR_VMAX)?.toIntOrNull()
            if (level != null) (level * 100 / 13).coerceIn(0, 100) else prefs.getInt("vibrator_strength", 85)
        } else prefs.getInt("vibrator_strength", 85)

        val hpGain = if (isRoot && isSupported(TuningFeature.HEADPHONE_GAIN)) {
            ShellUtils.fastRead(KernelNodes.HEADPHONE_GAIN_PROC)?.toIntOrNull()
                ?: prefs.getInt("headphone_gain", 0)
        } else prefs.getInt("headphone_gain", 0)

        val micGain = if (isRoot && isSupported(TuningFeature.MIC_GAIN)) {
            ShellUtils.fastRead(KernelNodes.MIC_GAIN_PROC)?.toIntOrNull()
                ?: prefs.getInt("mic_gain", 0)
        } else prefs.getInt("mic_gain", 0)

        val wakelock = if (isRoot && isSupported(TuningFeature.WAKELOCK_BLOCKER)) {
            ShellUtils.fastRead(KernelNodes.WAKELOCK_BLOCKER_PROC) == "1"
        } else prefs.getBoolean("wakelock_blocker", false)

        val fsync = if (isRoot && isSupported(TuningFeature.DYNAMIC_FSYNC)) {
            ShellUtils.fastRead(KernelNodes.DYNAMIC_FSYNC_PROC) == "1" ||
            ShellUtils.fastRead(KernelNodes.DYNAMIC_FSYNC_SYS) == "1"
        } else prefs.getBoolean("dynamic_fsync", false)

        val readAhead = if (isRoot) {
            blockQueues().firstNotNullOfOrNull { ShellUtils.readNode("$it/read_ahead_kb") }?.toIntOrNull()
                ?: prefs.getInt("read_ahead_kb", 256)
        } else prefs.getInt("read_ahead_kb", 256)

        val schedulerRaw = blockQueues().firstNotNullOfOrNull { ShellUtils.readNode("$it/scheduler") }.orEmpty()
        val (activeScheduler, schedulerOptions) = KernelNodes.parseSelection(schedulerRaw)

        val governorRaw = KernelNodes.cpufreqPolicies()
            .firstNotNullOfOrNull { ShellUtils.readNode("$it/cpufreq/scaling_governor") }
            .orEmpty()
        val (activeGovernor, governorOptions) = KernelNodes.parseSelection(governorRaw)

        val zramSize = ShellUtils.readNode("/sys/block/zram0/disksize")?.trim()?.toLongOrNull()?.div(1024 * 1024) ?: 0L

        TuningFeatures(
            gamingMode = gamingLevel,
            touchBoost240Hz = touchBoost,
            touchSensitivity = touchSensitivity,
            fastChargeEnabled = fastCharge,
            bypassCharging = bypass,
            chargeLimitPercent = chargeLimit,
            thermalGuardEnabled = thermalGuard,
            tempLimitC = tempLimit,
            xswapdEnabled = xswapdEnable,
            xswapdQuotaMb = xswapdQuota,
            doubleTapToWake = dt2w,
            trueToneDisplay = trueTone,
            colorMode = colorMode,
            hbmMode = hbmMode,
            vibratorStrengthPercent = vibrator,
            headphoneGainDb = hpGain,
            micGainLevel = micGain,
            wakelockBlockerEnabled = wakelock,
            dynamicFsync = fsync,
            readAheadKb = readAhead,
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

        val boosted = applyKernelWrites(
            successLabel = "${level.title} - EAS boost $boost",
            KernelNodes.GAMING_MODE_PROC to level.value.toString(),
            KernelNodes.GAMING_MODE_SYS to level.value.toString(),
            KernelNodes.BOOST_FG to boost.toString()
        )

        if (boost > 0) {
            applyTouchBoost(true, level)
        } else {
            applyTouchBoost(false, level)
        }

        boosted
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
        applyKernelWrites(
            successLabel = if (enable) "18W Fast Charging enabled" else "Standard charging enforced (500mA USB / 10W AC)",
            KernelNodes.FAST_CHARGE_PROC to if (enable) "1" else "0"
        )
    }

    suspend fun setBypassCharging(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("bypass_charging", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Bypass charging")
        if (!isSupported(TuningFeature.BYPASS_CHARGING)) return@withContext unsupported(TuningFeature.BYPASS_CHARGING)
        applyKernelWrites(
            successLabel = if (enable) "Direct-Power bypass mode active (charging disabled)" else "Normal charging enabled",
            KernelNodes.BATTERY_BYPASS_PROC to if (enable) "1" else "0",
            KernelNodes.BATTERY_BYPASS_SYS to if (enable) "1" else "0",
            KernelNodes.BATTERY_CHARGING_ENABLED to if (enable) "0" else "1"
        )
    }

    suspend fun setChargeLimit(percent: Int): TuningApplyResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(50, 100)
        prefs.edit().putInt("charge_limit", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Charge limit")
        if (!isSupported(TuningFeature.CHARGE_LIMIT)) return@withContext unsupported(TuningFeature.CHARGE_LIMIT)
        applyKernelWrites(
            successLabel = "Charging stop limit set to $clamped%",
            KernelNodes.BATTERY_LIMIT_PROC to clamped.toString(),
            KernelNodes.BATTERY_LIMIT_SYS to clamped.toString(),
            KernelNodes.BATTERY_CHARGE_CONTROL_LIMIT to clamped.toString()
        )
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

    // ---- xswapd: userspace daemon ----

    suspend fun setXswapdEnabled(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("xswapd_enable", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("xswapd")
        if (!isSupported(TuningFeature.XSWAPD)) return@withContext unsupported(TuningFeature.XSWAPD)
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
        if (!isSupported(TuningFeature.XSWAPD)) return@withContext unsupported(TuningFeature.XSWAPD)
        applyKernelWrites(
            successLabel = "Triggered 100MB xswapd memory reclaim",
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
        val clamped = mode.coerceIn(0, 3)
        prefs.edit().putInt("color_mode", clamped).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Color mode")
        if (!isSupported(TuningFeature.COLOR_MODE)) return@withContext unsupported(TuningFeature.COLOR_MODE)
        val label = when (clamped) {
            1 -> "Reference D65"
            2 -> "Vivid"
            3 -> "SLog3"
            else -> "Standard"
        }
        applyKernelWrites(
            successLabel = "Display color profile set to $label",
            KernelNodes.COLOR_MODE_PROC to clamped.toString()
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

    // ---- Storage ----

    suspend fun setDynamicFsync(enable: Boolean): TuningApplyResult = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("dynamic_fsync", enable).apply()
        if (!ShellUtils.isRootAvailable()) return@withContext notApplied("Dynamic Fsync")
        if (!isSupported(TuningFeature.DYNAMIC_FSYNC)) return@withContext unsupported(TuningFeature.DYNAMIC_FSYNC)
        applyKernelWrites(
            successLabel = if (enable) "Dynamic Fsync active" else "Dynamic Fsync disabled",
            KernelNodes.DYNAMIC_FSYNC_PROC to if (enable) "1" else "0",
            KernelNodes.DYNAMIC_FSYNC_SYS to if (enable) "1" else "0"
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
}