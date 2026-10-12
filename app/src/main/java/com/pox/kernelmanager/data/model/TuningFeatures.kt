package com.pox.kernelmanager.data.model

enum class GamingModeLevel(val value: Int, val title: String, val description: String) {
    BALANCED(0, "Balanced", "Standard governor with efficient energy scaling"),
    GAMING(1, "Gaming Mode", "Tight frame deadlines, touch boost & Mali-G76 boost"),
    EXTREME(2, "Extreme 2133MHz", "Floor lock CPU/GPU clocks, DRAM LP4-2100 max bandwidth")
}

data class TuningFeatures(
    // Gaming & Performance
    val gamingMode: GamingModeLevel = GamingModeLevel.BALANCED,
    val touchBoost240Hz: Boolean = true,
    val fpsgoUltraRescue: Boolean = true,

    // Smart Battery Guard & Bypass
    val bypassCharging: Boolean = false,
    val chargeLimitPercent: Int = 100,
    val thermalGuardEnabled: Boolean = false,
    val tempLimitC: Int = 48,

    // MIUI / HyperOS xswapd
    val xswapdEnabled: Boolean = true,
    val xswapdQuotaMb: Long = 0,

    // Hardware Touch & Display
    val doubleTapToWake: Boolean = true,
    val trueToneDisplay: Boolean = true,
    val vibratorStrengthPercent: Int = 85,

    // Storage & I/O
    val dynamicFsync: Boolean = true,
    val readAheadKb: Int = 128,
    val ioScheduler: String = "mq-deadline",
    val availableSchedulers: List<String> = listOf("mq-deadline", "bfq", "none")
)
