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
        subtitle = "Heightened responsiveness, 240Hz touch polling and fast app opening",
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
        subtitle = "Uncapped big-core boost, zero governor latency and peak hardware wattage",
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
        description = "Immediate EAS scheduling boost, high OPP floors and low input latency",
        icon = Icons.Default.Speed
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
    val baseOppCeilingKhz: Int = 2050000,
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

    fun getPreset(
        tier: EasyModeTier,
        profile: EasyModeProfile
    ): EasyPresetConfig {
        return when (tier) {
            EasyModeTier.LOW -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = -1,
                    kprofileLevel = 1, // Battery
                    schedBoreEnabled = true,
                    burstPenaltyScale = 2048,
                    burstPenaltyOffset = 32,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = true,
                    chargeWatt = 10, // Gentle 10W preserves battery chemistry
                    baseOppCeilingKhz = 1664000,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 3, // Longevity
                    summaryHighlights = listOf("1.66 GHz Eco Ceiling", "10W Gentle Charging", "BORE Background Freeze", "Cool Thermals")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = -1,
                    kprofileLevel = 1,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1792,
                    burstPenaltyOffset = 28,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = true,
                    chargeWatt = 12,
                    baseOppCeilingKhz = 1784000,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 3,
                    summaryHighlights = listOf("1.78 GHz Efficiency Cap", "12W Safe Charge", "Wakelock Shield", "Extended SOT")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1536,
                    burstPenaltyOffset = 24,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    baseOppCeilingKhz = 1989000,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Balanced Governor", "15W Standard Charge", "BORE Prioritization", "Stable Power")
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
                    burstPenaltyOffset = 26,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = true,
                    chargeWatt = 15,
                    baseOppCeilingKhz = 1989000,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Daily Battery Saver", "15W Safe Fast Charge", "BORE Tuned", "Auto-Adaptive Sag Guard")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1536,
                    burstPenaltyOffset = 24,
                    burstInheritType = 2,
                    touchGameMode = false,
                    touchSensitivity = 0,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Full 2.05 GHz Boost", "18W Standard Fast Charge", "Smooth 60 FPS", "Balanced Energy")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1280,
                    burstPenaltyOffset = 20,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Gaming Mode Boost", "Touch Boost 240Hz", "18W Fast Charge", "Snappy App Launches")
                )
            }

            EasyModeTier.PRO -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 0,
                    kprofileLevel = 2,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1400,
                    burstPenaltyOffset = 22,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 1,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Productivity Balance", "Touch Boost 240Hz", "18W Fast Charge", "Adaptive OPP")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1280,
                    burstPenaltyOffset = 20,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2, // Perf Guard
                    summaryHighlights = listOf("18W Rated Fast Charge", "Gaming Boost Active", "High Touch Sampling", "Zero UI Janks")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1024,
                    burstPenaltyOffset = 16,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W Fast Charge", "Aggressive BORE Scheduler", "Fast Task Dispatch", "Sustained FPS")
                )
            }

            EasyModeTier.ULTRA -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 1,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1280,
                    burstPenaltyOffset = 20,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 2,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W Fast Charge", "High-FPS Gaming", "Balanced Thermal Floor", "Adaptive OPP Guard")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2, // Extreme gaming governor
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1024,
                    burstPenaltyOffset = 16,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W Fast Charge", "Extreme EAS Boost 100", "240Hz Competitive Touch", "FPSGO Lock")
                )
                EasyModeProfile.MAX_PERFORMANCE -> EasyPresetConfig(
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
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W High-Current Fast Charge", "Peak Core OPP Lock", "Zero Throttle Governor", "Competitive Gaming")
                )
            }

            EasyModeTier.EXTREME -> when (profile) {
                EasyModeProfile.MAX_BATTERY -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1024,
                    burstPenaltyOffset = 16,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W Rated Max Charge", "Extreme EAS Boost", "Thermal Guard Active", "Peak GPU Clock")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2,
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 768,
                    burstPenaltyOffset = 8,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W Hardware Max Charge", "Uncapped Big Cores", "Minimal Burst Penalty", "Ultra Responsiveness")
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
                    chargeWatt = 18,
                    baseOppCeilingKhz = 2050000,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("18W PMIC Hardware Limit", "Zero Governor Latency", "Direct Core Affinity", "Benchmark / Record Setting")
                )
            }
        }
    }

    /**
     * Dynamically adapts a preset according to physical battery impedance,
     * transient voltage sag, die thermal rise gradient, and usage degradation.
     */
    fun getAdaptedPreset(tier: EasyModeTier, profile: EasyModeProfile, stats: LearningStats): AdaptedPresetConfig {
        val base = getPreset(tier, profile)
        return adaptPreset(base, stats)
    }

    fun adaptPreset(
        base: EasyPresetConfig,
        stats: LearningStats
    ): AdaptedPresetConfig {
        val notes = mutableListOf<String>()

        // 1. Safe Big Cluster OPP Ceiling
        var effectiveOpp = base.baseOppCeilingKhz
        val oppFloor = 1_100_000
        if (stats.maxSafeOppKhz in oppFloor until effectiveOpp) {
            val diffMhz = (effectiveOpp - stats.maxSafeOppKhz) / 1000
            effectiveOpp = stats.maxSafeOppKhz
            notes.add("⚡ Big cluster OPP capped to ${effectiveOpp / 1000} MHz (-${diffMhz} MHz) to prevent PMIC UVLO shutdown")
        }
        if (stats.batteryDegradationFactor < 0.85f && base.tier == EasyModeTier.LOW) {
            val sweetSpot = 1664000
            if (effectiveOpp > sweetSpot) {
                effectiveOpp = sweetSpot
                notes.add("🔋 Big cluster frequency clamped to ${sweetSpot / 1000} MHz efficiency sweet-spot for aged battery")
            }
        }

        // 2. Safe Charging Rate & Joule Heating Prevention
        val maxHardwareWatt = 18
        var effectiveWatt = base.chargeWatt.coerceIn(5, maxHardwareWatt)
        if (stats.dcirMohm >= 220 || stats.averageLoadTempC >= 41.0f) {
            val derate1 = 15
            if (effectiveWatt > derate1) {
                val delta = effectiveWatt - derate1
                effectiveWatt = derate1
                notes.add("🌡️ Fast charging tuned to ${derate1}W (-${delta}W) to curb Joule heating (DCIR: ${stats.dcirMohm} mΩ, Avg Temp: ${String.format(Locale.US, "%.1f", stats.averageLoadTempC)}°C)")
            }
            val derate2 = 12
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
}
