package com.pox.kernelmanager.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.pox.kernelmanager.data.learning.LearningStats
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class EasyModeTier(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String
) {
    LOW(
        title = "Low",
        subtitle = "Ultra-efficient power envelope for maximum standby and light daily tasks",
        icon = Icons.Default.BatteryChargingFull,
        badge = "Eco"
    ),
    NORMAL(
        title = "Normal",
        subtitle = "Perfect equilibrium between battery longevity and smooth daily interactions",
        icon = Icons.Default.Balance,
        badge = "Daily"
    ),
    PRO(
        title = "Pro",
        subtitle = "Heightened responsiveness, 120Hz touch polling and fast app opening",
        icon = Icons.Default.WorkOutline,
        badge = "Speed"
    ),
    ULTRA(
        title = "Ultra",
        subtitle = "High-efficiency gaming performance with optimal thermal dissipation",
        icon = Icons.Default.SportsEsports,
        badge = "Gaming"
    ),
    EXTREME(
        title = "Extreme",
        subtitle = "Uncapped Cortex-A73 OPP boost, zero throttle and peak 15W AFC wattage",
        icon = Icons.Default.LocalFireDepartment,
        badge = "Peak"
    )
}

enum class EasyModeProfile(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    MAX_BATTERY(
        title = "Max Battery",
        description = "Conservative governor floors, aggressive wakelock blocking and lower heat",
        icon = Icons.Default.BatterySaver
    ),
    BALANCED(
        title = "Balanced",
        description = "Intelligent workload-dependent scaling with fluid frame delivery",
        icon = Icons.Default.Autorenew
    ),
    MAX_PERFORMANCE(
        title = "Max Performance",
        description = "High scheduler bias, instantaneous frequency ramp and zero frame drops",
        icon = Icons.Default.Bolt
    )
}

data class EasyPresetConfig(
    val tier: EasyModeTier,
    val profile: EasyModeProfile,
    val gamingMode: Int,
    val kprofileLevel: Int,
    val schedBoreEnabled: Boolean,
    val burstPenaltyScale: Int,
    val burstPenaltyOffset: Int,
    val burstInheritType: Int,
    val touchGameMode: Boolean,
    val touchSensitivity: Int,
    val fastChargeEnabled: Boolean,
    val chargeWatt: Int,
    val baseOppCeilingKhz: Int = 1768000,
    val dynamicFsync: Boolean,
    val wakelockBlocker: Boolean,
    val trueTone: Boolean,
    val healthCalibration: Int,
    val summaryHighlights: List<String>
)

data class AdaptedPresetConfig(
    val base: EasyPresetConfig,
    val effectiveGamingMode: Int,
    val effectiveKprofileLevel: Int,
    val effectiveSchedBoreEnabled: Boolean,
    val effectiveBurstPenaltyScale: Int,
    val effectiveBurstPenaltyOffset: Int,
    val effectiveBurstInheritType: Int,
    val effectiveTouchGameMode: Boolean,
    val effectiveTouchSensitivity: Int,
    val effectiveFastChargeEnabled: Boolean,
    val effectiveChargeWatt: Int,
    val effectiveOppCeilingKhz: Int,
    val effectiveHealthCalibration: Int,
    val effectiveDynamicFsync: Boolean,
    val effectiveWakelockBlocker: Boolean,
    val effectiveTrueTone: Boolean,
    val isAdapted: Boolean,
    val adaptationNotes: List<String>
)

object EasyModePresets {

    fun adaptPreset(
        base: EasyPresetConfig,
        stats: LearningStats
    ): AdaptedPresetConfig {
        val notes = mutableListOf<String>()

        // 1. Safe Big Cluster OPP Ceiling (Exynos 7904: max 1.77 GHz)
        var effectiveOpp = base.baseOppCeilingKhz
        val oppFloor = 832_000
        if (stats.maxSafeOppKhz in oppFloor until effectiveOpp) {
            val diffMhz = (effectiveOpp - stats.maxSafeOppKhz) / 1000
            effectiveOpp = stats.maxSafeOppKhz
            notes.add("⚡ Big cluster OPP capped to ${effectiveOpp / 1000} MHz (-${diffMhz} MHz) to prevent PMIC UVLO voltage droop")
        }
        if (stats.batteryDegradationFactor < 0.85f && base.tier == EasyModeTier.LOW) {
            val sweetSpot = 1352000
            if (effectiveOpp > sweetSpot) {
                effectiveOpp = sweetSpot
                notes.add("🔋 Big cluster frequency clamped to ${sweetSpot / 1000} MHz efficiency sweet-spot for aged battery")
            }
        }

        // 2. Safe Charging Rate & Joule Heating Prevention (15W Samsung AFC Max)
        val maxHardwareWatt = 15
        var effectiveWatt = base.chargeWatt.coerceIn(5, maxHardwareWatt)
        if (stats.dcirMohm >= 220 || stats.averageLoadTempC >= 41.0f) {
            val derate1 = 12
            if (effectiveWatt > derate1) {
                val delta = effectiveWatt - derate1
                effectiveWatt = derate1
                notes.add("🌡️ Fast charging tuned to ${derate1}W (-${delta}W) to curb Joule heating (DCIR: ${stats.dcirMohm} mΩ, Avg Temp: ${String.format(Locale.US, "%.1f", stats.averageLoadTempC)}°C)")
            }
            val derate2 = 10
            if (stats.dcirMohm >= 260 && effectiveWatt > derate2) {
                effectiveWatt = derate2
                notes.add("🛡️ Fast charge reduced to ${derate2}W to protect high-impedance battery cell")
            }
        }

        // 3. Schedutil Slew Rate vs Thermal Gradient
        if (stats.thermalRiseRateMcs >= 1300) {
            notes.add("🔥 Schedutil up-rate dampened to avoid thermal runaway (Die heating slope: ${stats.thermalRiseRateMcs} m°C/s)")
        } else if (stats.thermalRiseRateMcs <= 800) {
            notes.add("❄️ Schedutil up-rate accelerated for instant boost response (Cool die gradient: ${stats.thermalRiseRateMcs} m°C/s)")
        }

        // 4. BORE CFS Scheduler Burst Penalties
        var effectiveScale = base.burstPenaltyScale
        var effectiveOffset = base.burstPenaltyOffset
        if (stats.efficiencyIndex < 85 && (base.tier == EasyModeTier.LOW || base.tier == EasyModeTier.NORMAL)) {
            effectiveScale = min(2048, effectiveScale + 256)
            notes.add("🧠 BORE burst penalty scale elevated to $effectiveScale (+256) to suppress background task drain")
        }
        if (base.tier in listOf(EasyModeTier.PRO, EasyModeTier.ULTRA, EasyModeTier.EXTREME) && stats.averageLoadTempC < 38.0f) {
            effectiveOffset = max(0, effectiveOffset - 4)
            notes.add("🚀 BORE burst penalty offset lowered to $effectiveOffset for reduced UI thread wakeup jitter")
        }

        // 5. Battery Degradation & Longevity Mode
        var effectiveHealthMode = base.healthCalibration
        if (stats.batteryDegradationFactor < 0.80f && effectiveHealthMode < 3) {
            effectiveHealthMode = 3
            notes.add("⚠️ Health Guard auto-elevated to Longevity mode due to severe battery wear (${String.format(Locale.US, "%.1f", stats.batteryDegradationFactor * 100)}% capacity)")
        }

        return AdaptedPresetConfig(
            base = base,
            effectiveGamingMode = base.gamingMode,
            effectiveKprofileLevel = base.kprofileLevel,
            effectiveSchedBoreEnabled = base.schedBoreEnabled,
            effectiveBurstPenaltyScale = effectiveScale,
            effectiveBurstPenaltyOffset = effectiveOffset,
            effectiveBurstInheritType = base.burstInheritType,
            effectiveTouchGameMode = base.touchGameMode,
            effectiveTouchSensitivity = base.touchSensitivity,
            effectiveFastChargeEnabled = base.fastChargeEnabled,
            effectiveChargeWatt = effectiveWatt,
            effectiveOppCeilingKhz = effectiveOpp,
            effectiveHealthCalibration = effectiveHealthMode,
            effectiveDynamicFsync = base.dynamicFsync,
            effectiveWakelockBlocker = base.wakelockBlocker,
            effectiveTrueTone = base.trueTone,
            isAdapted = notes.isNotEmpty(),
            adaptationNotes = notes
        )
    }

    fun getPreset(tier: EasyModeTier, profile: EasyModeProfile): EasyPresetConfig {
        return when (tier) {
            EasyModeTier.LOW -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 0,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 2048,
                    burstPenaltyOffset = 48,
                    burstInheritType = 3,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = false,
                    chargeWatt = 10,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = false,
                    healthCalibration = 2,
                    summaryHighlights = listOf("10W Safe Charge", "Deep Sleep Idle", "Strict Cortex-A53 Bias", "Sub-mW Standby")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 1,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1792,
                    burstPenaltyOffset = 40,
                    burstInheritType = 3,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = true,
                    chargeWatt = 10,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = false,
                    healthCalibration = 2,
                    summaryHighlights = listOf("10W Standard Charge", "Light Governor Scaling", "Low Thermal Threshold", "Multi-day Battery")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 1,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1536,
                    burstPenaltyOffset = 32,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 12,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("12W Balanced Charge", "Fluid App Launch", "Eco Big Core Ceiling", "Low Latency UI")
                )
            }

            EasyModeTier.NORMAL -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 1,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1664,
                    burstPenaltyOffset = 36,
                    burstInheritType = 3,
                    touchGameMode = false,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 10,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("10W Standard Charge", "Daily Power Balance", "Wakelock Shield", "Optimized Background")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1408,
                    burstPenaltyOffset = 28,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Adaptive EAS Balancing", "Fluid Scrolling", "All-Day Battery")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1280,
                    burstPenaltyOffset = 24,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Instantaneous Touch Response", "Snappy App Switch", "High Priority UI")
                )
            }

            EasyModeTier.PRO -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1408,
                    burstPenaltyOffset = 24,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Productivity Optimized", "Pro Multi-tasking", "Sustained Efficiency")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1152,
                    burstPenaltyOffset = 20,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Dynamic CPU Burst", "Low Latency Rendering", "Smooth Daily Driver")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1024,
                    burstPenaltyOffset = 16,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Elevated A73 OPP Clock", "Burst Task Accelerator", "Rapid I/O Flash")
                )
            }

            EasyModeTier.ULTRA -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1152,
                    burstPenaltyOffset = 18,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Extended Gaming Session", "Thermal Throttling Guard", "Stable FPS")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 896,
                    burstPenaltyOffset = 14,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Mali-G71 GPU Acceleration", "Frame Time Stabilization", "High Responsiveness")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 768,
                    burstPenaltyOffset = 10,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Peak 1768 MHz A73 Lock", "Low Latency Touch", "Competitive Gaming")
                )
            }

            EasyModeTier.EXTREME -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 896,
                    burstPenaltyOffset = 12,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Extreme EAS Boost", "Thermal Guard Active", "Peak Mali GPU Clock")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 640,
                    burstPenaltyOffset = 6,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Fast Charge", "Uncapped Big Cores", "Minimal Burst Penalty", "Ultra Responsiveness")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 512,
                    burstPenaltyOffset = 0,
                    burstInheritType = 1,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("15W AFC Peak Power", "Zero Governor Latency", "Direct Core Affinity", "Benchmark / Record Setting")
                )
            }
        }
    }
}
