package com.pox.kernelmanager.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

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
        subtitle = "Uncapped big-core boost, zero throttle and peak hardware wattage",
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
    val dynamicFsync: Boolean,
    val wakelockBlocker: Boolean,
    val trueTone: Boolean,
    val healthCalibration: Int,
    val summaryHighlights: List<String>
)

object EasyModePresets {

    fun getPreset(tier: EasyModeTier, profile: EasyModeProfile): EasyPresetConfig {
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
                    chargeWatt = 10,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 3, // Longevity
                    summaryHighlights = listOf("Powersave Governor", "10W Gentle Charging", "Max Background Freeze", "Cool Temp")
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
                    chargeWatt = 15,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 3,
                    summaryHighlights = listOf("Low OPP Ceiling", "15W Standard Charge", "Wakelock Shield", "Extended SOT")
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
                    chargeWatt = 18,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Balanced Governor", "18W Fast Charge", "Mild Boost", "Stable Power")
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
                    chargeWatt = 18,
                    dynamicFsync = true,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Daily Battery Saver", "18W Fast Charge", "BORE Tuned", "Auto-Adaptive OPP")
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
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 1,
                    summaryHighlights = listOf("Default Kernel Spec", "18W Standard Fast Charge", "Smooth 60 FPS", "Balanced Energy")
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
                    chargeWatt = 27,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2, // Perf Guard
                    summaryHighlights = listOf("27W Quick Charge", "Gaming Boost Active", "High Touch Sampling", "Zero UI Janks")
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
                    chargeWatt = 30,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("30W Quick Charge", "Aggressive BORE Scheduler", "Fast Task Dispatch", "Sustained FPS")
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
                    chargeWatt = 30,
                    dynamicFsync = false,
                    wakelockBlocker = true,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("30W Charge Lock", "High-FPS Gaming", "Balanced Thermal Floor", "Bypass Ready")
                )
                EasyModeProfile.BALANCED -> EasyPresetConfig(
                    tier = tier,
                    profile = profile,
                    gamingMode = 2, // Extreme gaming
                    kprofileLevel = 3,
                    schedBoreEnabled = true,
                    burstPenaltyScale = 1024,
                    burstPenaltyOffset = 16,
                    burstInheritType = 2,
                    touchGameMode = true,
                    touchSensitivity = 3,
                    fastChargeEnabled = true,
                    chargeWatt = 45,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("45W Flash Charge", "Extreme EAS Boost 100", "240Hz Competitive Touch", "FPSGO Lock")
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
                    chargeWatt = 67,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("67W Turbo Charge", "Peak Core OPP Lock", "Zero Throttle Governor", "Competitive Gaming")
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
                    chargeWatt = 45,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("45W High Power", "Extreme EAS Boost", "Thermal Guard Active", "Peak GPU Clock")
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
                    chargeWatt = 67,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("67W Ultra Charge", "Uncapped Big Cores", "Minimal Burst Penalty", "Ultra Responsiveness")
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
                    chargeWatt = 90,
                    dynamicFsync = false,
                    wakelockBlocker = false,
                    trueTone = true,
                    healthCalibration = 2,
                    summaryHighlights = listOf("90W Maximum Hardware Charge", "Zero Governor Latency", "Direct Core Affinity", "Benchmark / Record Setting")
                )
            }
        }
    }
}
