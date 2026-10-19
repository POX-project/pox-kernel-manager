package com.pox.kernelmanager.data.model

data class CpuCoreStat(
    val coreId: Int,
    val currentFreqMhz: Int,
    val minFreqMhz: Int,
    val maxFreqMhz: Int,
    val isBigCore: Boolean // Cortex-A76 cores 6,7 vs Cortex-A55 cores 0-5
)

data class HardwareStats(
    val cpuCores: List<CpuCoreStat> = emptyList(),
    val cpuGovernor: String = "schedutil",
    val gpuFreqMhz: Int = 0,
    val gpuLoadPercent: Int = 0,
    val ramTotalMb: Long = 0,
    val ramUsedMb: Long = 0,
    val ramFreeMb: Long = 0,
    val zramTotalMb: Long = 0,
    val zramUsedMb: Long = 0,
    val zramCompRatio: Float = 0f,
    val batteryLevel: Int = 0,
    val batteryVoltageMv: Int = 0,
    val batteryCurrentMa: Int = 0,
    val batteryWattageW: Float = 0f,
    val batteryTempC: Float = 0f,
    val batteryStatus: String = "Discharging",
    val isBypassActive: Boolean = false,
    val chargerSource: String = "Battery",
    val isCharging: Boolean = false,
    val isFastCharging: Boolean = false,
    val batteryHealth: String = "Good",
    val batteryTechnology: String = "Li-poly",
    val chargeLimitPercent: Int = 100,
    val designCapacityMah: Int = 4500,
    val estimatedCapacityMah: Int = 4385,
    val chargeRateMaPerHr: Int = 0
)
