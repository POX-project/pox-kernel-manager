package com.pox.kernelmanager.data.model

/**
 * A tuning control the UI can expose.
 *
 * Existence is decided at runtime by probing the kernel, because POX ships
 * several editions and the available nodes differ between them.
 */
enum class TuningFeature(val label: String) {
    GAMING_MODE("Gaming mode"),
    TOUCH_BOOST("Touch boost"),
    FPS_CAP("FPS cap"),
    BYPASS_CHARGING("Bypass charging"),
    CHARGE_LIMIT("Charge limit"),
    THERMAL_GUARD("Thermal guard"),
    XSWAPD("xswapd"),
    DT2W("Double tap to wake"),
    TRUE_TONE("True Tone"),
    VIBRATOR_STRENGTH("Vibrator strength"),
    DYNAMIC_FSYNC("Dynamic fsync"),
    READ_AHEAD("Read-ahead")
}

enum class GamingModeLevel(val value: Int, val title: String, val description: String) {
    BALANCED(0, "Balanced", "Standard governor with efficient energy scaling"),
    GAMING(1, "Gaming Mode", "EAS boost 50 + FPSGO touch boost"),
    EXTREME(2, "Extreme", "EAS boost 100 + FPSGO touch boost at max OPP")
}

data class TuningFeatures(
    // Gaming & Performance
    val gamingMode: GamingModeLevel = GamingModeLevel.BALANCED,
    val touchBoost240Hz: Boolean = false,

    // Smart Battery Guard & Bypass (no node in pox-begonia-kernel)
    val bypassCharging: Boolean = false,
    val chargeLimitPercent: Int = 100,
    val thermalGuardEnabled: Boolean = false,
    val tempLimitC: Int = 48,

    // MIUI / HyperOS xswapd (userspace daemon, not present on stock)
    val xswapdEnabled: Boolean = false,
    val xswapdQuotaMb: Long = 0,

    // Hardware Touch & Display (no node in pox-begonia-kernel)
    val doubleTapToWake: Boolean = false,
    val trueToneDisplay: Boolean = false,
    val vibratorStrengthPercent: Int = 85,

    // Storage & I/O
    val dynamicFsync: Boolean = false,
    val readAheadKb: Int = 256,

    // begonia builds CONFIG_DEFAULT_IOSCHED="noop" with mq-deadline/kyber available,
    // so "mq-deadline" is not a scheduler this kernel can select.
    val ioScheduler: String = "noop",
    val availableSchedulers: List<String> = listOf("noop", "deadline", "kyber"),

    /** Features this kernel does not implement, so the UI can disable them. */
    val unsupported: Set<TuningFeature> = emptySet()
) {
    fun isSupported(feature: TuningFeature): Boolean = feature !in unsupported
}