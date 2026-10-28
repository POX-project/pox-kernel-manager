package com.pox.kernelmanager.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.pox.kernelmanager.data.learning.LearningStats
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class BrainlessProfile(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badge: String
) {
    BATTERY_BACKUP(
        title = "Battery Backup",
        subtitle = "Maximum endurance & extended SOT",
        description = "Conservative governor floors, 10W-12W cool gentle charging, aggressive background task freezing, and low thermals.",
        icon = Icons.Default.BatteryChargingFull,
        badge = "Eco"
    ),
    STABILITY(
        title = "Stability",
        subtitle = "Rock-solid daily driver balance",
        description = "Stock-like reliability with fluid 60 FPS frame delivery, standard 18W fast charge, and balanced energy consumption.",
        icon = Icons.Default.Verified,
        badge = "Balanced"
    ),
    PERFORMANCE(
        title = "Performance",
        subtitle = "Peak responsiveness & high-FPS gaming",
        description = "Instant 240Hz touch boost, fast app launches, high CPU governor bias, and sustained gaming frame pacing.",
        icon = Icons.Default.Bolt,
        badge = "Speed"
    )
}

data class BrainlessConfig(
    val profile: BrainlessProfile,
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
    val baseOppCeilingKhz: Int,
    val dynamicFsync: Boolean,
    val wakelockBlocker: Boolean,
    val trueTone: Boolean,
    val healthCalibration: Int,
    val highlights: List<String>
)

data class AdaptedBrainlessConfig(
    val base: BrainlessConfig,
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

object BrainlessPresets {

    fun getPreset(profile: BrainlessProfile): BrainlessConfig {
        return when (profile) {
            BrainlessProfile.BATTERY_BACKUP -> BrainlessConfig(
                profile = profile,
                gamingMode = -1, // Powersave
                kprofileLevel = 1, // Battery
                schedBoreEnabled = true,
                burstPenaltyScale = 2048,
                burstPenaltyOffset = 32,
                burstInheritType = 2,
                touchGameMode = false,
                touchSensitivity = 0,
                fastChargeEnabled = true,
                chargeWatt = 10,
                baseOppCeilingKhz = 1664000,
                dynamicFsync = true,
                wakelockBlocker = true,
                trueTone = true,
                healthCalibration = 3, // Longevity mode
                highlights = listOf("1.66 GHz Eco Ceiling", "10W Gentle Charging", "Max Background Freeze", "Cool Thermals")
            )
            BrainlessProfile.STABILITY -> BrainlessConfig(
                profile = profile,
                gamingMode = 0, // Balanced
                kprofileLevel = 2, // Balanced
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
                healthCalibration = 1, // Auto-Adaptive
                highlights = listOf("Full 2.05 GHz Boost", "18W Standard Fast Charge", "Smooth 60 FPS", "Zero Crashes")
            )
            BrainlessProfile.PERFORMANCE -> BrainlessConfig(
                profile = profile,
                gamingMode = 1, // Gaming
                kprofileLevel = 3, // Performance
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
                healthCalibration = 2, // Perf Guard
                highlights = listOf("Gaming Mode Boost", "Touch Boost 240Hz", "18W Fast Charge", "Sustained FPS")
            )
        }
    }

    fun adaptPreset(
        base: BrainlessConfig,
        stats: LearningStats
    ): AdaptedBrainlessConfig {
        val notes = mutableListOf<String>()

        // 1. Safe Big Cluster OPP Ceiling
        var effectiveOpp = base.baseOppCeilingKhz
        val oppFloor = 1_100_000
        if (stats.maxSafeOppKhz in oppFloor until effectiveOpp) {
            val diffMhz = (effectiveOpp - stats.maxSafeOppKhz) / 1000
            effectiveOpp = stats.maxSafeOppKhz
            notes.add("⚡ Big cluster OPP capped to ${effectiveOpp / 1000} MHz (-${diffMhz} MHz) to prevent PMIC UVLO voltage droop")
        }
        if (stats.batteryDegradationFactor < 0.85f && base.profile == BrainlessProfile.BATTERY_BACKUP) {
            val sweetSpot = 1664000
            if (effectiveOpp > sweetSpot) {
                effectiveOpp = sweetSpot
                notes.add("🔋 Big cluster frequency clamped to ${sweetSpot / 1000} MHz efficiency sweet-spot for aged cell")
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
        if (stats.efficiencyIndex < 85 && base.profile != BrainlessProfile.PERFORMANCE) {
            effectiveScale = min(2048, effectiveScale + 256)
            notes.add("🧠 BORE burst penalty scale elevated to $effectiveScale (+256) to suppress background task drain")
        }
        if (base.profile == BrainlessProfile.PERFORMANCE && stats.averageLoadTempC < 38.0f) {
            effectiveOffset = max(0, effectiveOffset - 4)
            notes.add("🚀 BORE burst penalty offset lowered to $effectiveOffset for reduced UI thread wakeup jitter")
        }

        // 5. Battery Degradation & Longevity Mode
        var effectiveHealthMode = base.healthCalibration
        if (stats.batteryDegradationFactor < 0.80f && effectiveHealthMode < 3) {
            effectiveHealthMode = 3
            notes.add("⚠️ Health Guard auto-elevated to Longevity mode due to severe battery wear (${String.format(Locale.US, "%.1f", stats.batteryDegradationFactor * 100)}% capacity)")
        }

        return AdaptedBrainlessConfig(
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
