package com.pox.kernelmanager.data.model

/**
 * A tuning control the UI can expose.
 *
 * Existence is decided at runtime by probing the kernel, because POX ships
 * several editions and the available nodes differ between them.
 */
enum class TuningFeature(val label: String, val unsupportedReason: String) {
    GAMING_MODE("Gaming mode", "Grant root - /proc/perfmgr/gaming_mode is not writable"),
    KPROFILES("Kprofiles", "Grant root - /sys/kernel/kprofiles/kp_mode is not writable"),
    PHONE_HEALTH_CALIBRATION("Phone health calibration", "Grant root - /proc/perfmgr/health_calibration is not writable"),
    BORE_SCHEDULER("BORE scheduler", "Grant root - /proc/perfmgr/sched_bore is not writable"),
    TOUCH_BOOST("Touch boost", "Grant root - /proc/perfmgr/touch_game_mode or usrtch is not writable"),
    TOUCH_SENSITIVITY("Touch sensitivity", "Grant root - /proc/perfmgr/touch_sensitivity is not writable"),
    FPS_CAP("FPS cap", "Grant root - /proc/perfmgr/syslimiter is not writable"),

    FAST_CHARGE("Fast charging", "Grant root - /proc/perfmgr/fast_charge is not writable"),
    BYPASS_CHARGING("Bypass charging", "Grant root - /proc/perfmgr/battery_bypass is not writable"),
    CHARGE_LIMIT("Charge limit", "Grant root - /proc/perfmgr/battery_limit is not writable"),
    THERMAL_GUARD("Thermal guard", "Grant root - /sys/kernel/battery_protection/thermal_guard is not writable"),
    DT2W("Double tap to wake", "Grant root - /proc/perfmgr/dt2w is not writable"),
    TRUE_TONE("True Tone", "Grant root - /proc/perfmgr/true_tone is not writable"),
    COLOR_MODE("Color mode", "Grant root - /proc/perfmgr/color_mode is not writable"),
    HBM_MODE("High brightness mode", "Grant root - /proc/perfmgr/hbm_mode is not writable"),
    HEADPHONE_GAIN("Headphone gain", "Grant root - /proc/perfmgr/headphone_gain is not writable"),
    MIC_GAIN("Microphone gain", "Grant root - /proc/perfmgr/mic_gain is not writable"),
    ACTIVE_MIC("Active microphone", "Grant root - /proc/perfmgr/active_mic is not writable"),
    VIBRATOR_STRENGTH("Vibrator strength", "Grant root - /proc/perfmgr/vibrator_strength is not writable"),
    WAKELOCK_BLOCKER("Wakelock blocker", "Grant root - /proc/perfmgr/wakelock_blocker is not writable"),

    CAMERA_4K60("Camera 4K60 FPS", "Grant root - /proc/perfmgr/camera_4k60 is not writable"),
    CAMERA_SLOG3("Sony S-Log3 Cinema", "Grant root - /proc/perfmgr/slog3 is not writable"),
    TORCH_BRIGHTNESS("Flashlight overdrive", "Grant root - /proc/perfmgr/torch_brightness is not writable"),

    WORKQUEUE_POWER_EFFICIENT("Workqueue power efficiency", "Grant root - /sys/module/workqueue/parameters/power_efficient is not writable"),
    TCP_CONGESTION("TCP congestion control", "Grant root - /proc/sys/net/ipv4/tcp_congestion_control is not writable"),
    TTL_64("Tethering TTL 64 uncap", "Grant root or iptables TTL target is unavailable"),

    WALT_CPU_UTIL("WALT CPU utilization", "Grant root - /proc/sys/kernel/sched_use_walt_cpu_util is not writable"),
    WALT_TASK_UTIL("WALT task utilization", "Grant root - /proc/sys/kernel/sched_use_walt_task_util is not writable"),
    SCHED_CSTATE_AWARE("C-State aware scheduling", "Grant root - /proc/sys/kernel/sched_cstate_aware is not writable"),
    VM_SWAPPINESS("VM swappiness", "Grant root - /proc/sys/vm/swappiness is not writable"),

    // Present only on some ROMs or with a module installed.
    XSWAPD("xswapd", "Needs /dev/memcg - MIUI/HyperOS ROMs only"),
    DYNAMIC_FSYNC("Dynamic fsync", "Grant root - /proc/perfmgr/dynamic_fsync is not writable"),
    DRIVEDROID("DriveDroid USB CD-ROM", "Grant root - /proc/perfmgr/usb_cdrom or LUN cdrom attribute is not writable"),

    // Standard Linux interfaces, available on every root solution.
    READ_AHEAD("Read-ahead", "No writable block queue found under /sys/block"),
    IO_SCHEDULER("I/O scheduler", "No writable queue scheduler found under /sys/block"),
    CPU_GOVERNOR("CPU governor", "No writable cpufreq governor found"),
    ZRAM_SIZE("ZRAM size", "/sys/block/zram0/disksize is not writable")
}

enum class GamingModeLevel(val value: Int, val title: String, val description: String) {
    BALANCED(0, "Balanced", "Standard governor with efficient energy scaling"),
    GAMING(1, "Gaming Mode", "EAS boost 50 + FPSGO touch boost"),
    EXTREME(2, "Extreme", "EAS boost 100 + FPSGO touch boost at max OPP")
}

enum class KprofilesLevel(val value: Int, val title: String, val description: String) {
    DISABLED(0, "Disabled", "Default stock scaling without profile overrides"),
    BATTERY(1, "Battery", "Aggressive energy saving and CPU frequency ceiling"),
    BALANCED(2, "Balanced", "Smooth responsive scaling with high power efficiency"),
    PERFORMANCE(3, "Performance", "Peak responsiveness, high OPP floor and zero frame-drops")
}

enum class HealthCalibrationMode(val value: Int, val title: String, val description: String) {
    DISABLED(0, "Disabled", "Standard vendor thermal and battery tables"),
    AUTO_ADAPTIVE(1, "Auto-Adaptive", "Calibrates OPP ceilings to live battery degradation to prevent brownout reboots and eliminate freezes"),
    PERF_GUARD(2, "Performance Guard", "Maintains high clock speeds while monitoring voltage drop"),
    LONGEVITY(3, "Maximum Longevity", "Limits big core voltage strain to extend battery lifespan")
}

data class TuningFeatures(
    // Gaming & Performance
    val gamingMode: GamingModeLevel = GamingModeLevel.BALANCED,
    val kprofilesMode: KprofilesLevel = KprofilesLevel.BALANCED,
    val autoKpEnabled: Boolean = true,
    val touchBoost240Hz: Boolean = false,
    val touchSensitivity: Int = 0,

    // BORE Scheduler & Interactive Response
    val schedBoreEnabled: Boolean = true,
    val schedBurstPenaltyScale: Int = 1536,
    val schedBurstPenaltyOffset: Int = 24,
    val schedBurstInheritType: Int = 2,

    // Phone Health & Silicon OPP Table Calibration
    val healthCalibrationMode: HealthCalibrationMode = HealthCalibrationMode.AUTO_ADAPTIVE,
    val oppVoltageGuard: Boolean = true,
    val healthStatusSummary: String = "Calibrated (100% Health) • Watchdog Armed",

    // Smart Battery Guard & Bypass
    val fastChargeEnabled: Boolean = true,
    val bypassCharging: Boolean = false,
    val chargeLimitPercent: Int = 100,
    val thermalGuardEnabled: Boolean = false,
    val tempLimitC: Int = 48,

    // MIUI / HyperOS xswapd (userspace daemon, not present on stock)
    val xswapdEnabled: Boolean = false,
    val xswapdQuotaMb: Long = 0,

    // Hardware Touch & Display
    val doubleTapToWake: Boolean = false,
    val trueToneDisplay: Boolean = false,
    val colorMode: Int = 0,
    val hbmMode: Int = 0,
    val vibratorStrengthPercent: Int = 85,
    val headphoneGainDb: Int = 0,
    val micGainLevel: Int = 0,
    val activeMicMode: Int = 0,
    val wakelockBlockerEnabled: Boolean = false,

    val dynamicFsync: Boolean = false,
    val driveDroidCdrom: Boolean = false,
    val readAheadKb: Int = 256,

    // Camera & Media Studio
    val camera4k60Enabled: Boolean = false,
    val slog3Enabled: Boolean = false,
    val torchBrightness: Int = 0,

    // Network & Tethering
    val tcpCongestion: String = "bbr",
    val availableTcpCongestions: List<String> = emptyList(),
    val ttl64Enabled: Boolean = false,

    // Advanced Kernel Sched & Power
    val wqPowerEfficient: Boolean = true,
    val waltCpuUtil: Boolean = false,
    val waltTaskUtil: Boolean = false,
    val schedCstateAware: Boolean = false,

    // Memory Tuning
    val vmSwappiness: Int = 100,
    val vfsCachePressure: Int = 100,

    // begonia builds CONFIG_DEFAULT_IOSCHED="noop" and compiles noop, deadline,
    // cfq and the mq variants kyber/deadline, so "mq-deadline" is not selectable.
    // Both lists are read back from the kernel rather than assumed.
    val ioScheduler: String = "",
    val availableSchedulers: List<String> = emptyList(),

    // begonia compiles schedutil, performance, powersave, ondemand and userspace.
    val cpuGovernor: String = "schedutil",
    val availableGovernors: List<String> = emptyList(),

    val zramSizeMb: Long = 0,

    /**
     * Whether this app holds an active root shell.
     *
     * Kept separate from [unsupported] because libsu silently falls back to a
     * plain `sh` when `su` is denied. Without this, "Magisk has not granted root
     * to the app" was indistinguishable from "this kernel has no such node", and
     * every control was greyed out with the wrong explanation.
     */
    val rootGranted: Boolean = false,

    /** Root provider detected, for the status line. */
    val rootProviderLabel: String = "",

    /** Features this kernel does not implement, so the UI can disable them. */
    val unsupported: Set<TuningFeature> = emptySet()
) {
    /**
     * Whether a control can be used.
     *
     * Without root nothing can be written regardless of what the kernel has, so
     * the root check comes first.
     */
    fun isSupported(feature: TuningFeature): Boolean =
        rootGranted && feature !in unsupported

    /** Why a control is disabled, for display next to it. */
    fun reasonFor(feature: TuningFeature): String? = when {
        !rootGranted ->
            "Pox Kernel Manager has not been granted root. Open your root manager (APatch / KernelSU / Magisk), " +
                "grant this app, then reopen it."
        feature in unsupported -> feature.unsupportedReason
        else -> null
    }
}