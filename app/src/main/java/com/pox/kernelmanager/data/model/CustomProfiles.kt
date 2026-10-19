package com.pox.kernelmanager.data.model

import org.json.JSONObject
import java.util.UUID

data class CustomColorProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Custom Profile",
    val description: String = "",
    val saturation: Int = 4,
    val contrast: Int = 4,
    val brightness: Int = 4,
    val saturationAdjustment: Int = 0,
    val isPreset: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("saturation", saturation)
        put("contrast", contrast)
        put("brightness", brightness)
        put("saturationAdjustment", saturationAdjustment)
        put("isPreset", isPreset)
    }

    companion object {
        fun fromJson(json: JSONObject): CustomColorProfile = CustomColorProfile(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Custom Profile"),
            description = json.optString("description", ""),
            saturation = json.optInt("saturation", 4),
            contrast = json.optInt("contrast", 4),
            brightness = json.optInt("brightness", 4),
            saturationAdjustment = json.optInt("saturationAdjustment", 0),
            isPreset = json.optBoolean("isPreset", false)
        )

        val DEFAULT_PRESETS = listOf(
            CustomColorProfile(
                id = "preset_amoled_punch",
                name = "AMOLED Deep Contrast",
                description = "High dynamic range with deep punchy blacks and enhanced saturation",
                saturation = 8,
                contrast = 7,
                brightness = 4,
                saturationAdjustment = 2,
                isPreset = true
            ),
            CustomColorProfile(
                id = "preset_warm_night",
                name = "Warm Reading (Anti-Blue)",
                description = "Relaxing eye comfort with soft muted contrast for low light reading",
                saturation = 3,
                contrast = 3,
                brightness = 3,
                saturationAdjustment = 0,
                isPreset = true
            ),
            CustomColorProfile(
                id = "preset_cyberpunk",
                name = "Cyberpunk Vibrant Neon",
                description = "Maximum saturation pop for gaming and vivid anime media",
                saturation = 11,
                contrast = 8,
                brightness = 5,
                saturationAdjustment = 3,
                isPreset = true
            ),
            CustomColorProfile(
                id = "preset_studio_flat",
                name = "Studio Monitor Flat",
                description = "Bit-perfect calibrated neutral baseline with zero digital clipping",
                saturation = 4,
                contrast = 4,
                brightness = 4,
                saturationAdjustment = 0,
                isPreset = true
            )
        )
    }
}

data class CustomKprofile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Custom Profile",
    val description: String = "",
    val gamingMode: GamingModeLevel = GamingModeLevel.BALANCED,
    val touchGameMode: Boolean = false,
    val touchSensitivity: Int = 0,
    val schedBore: Boolean = true,
    val schedBurstPenaltyScale: Int = 1536,
    val schedBurstPenaltyOffset: Int = 24,
    val schedBurstInheritType: Int = 2,
    val dynamicFsync: Boolean = true,
    val fastCharge: Boolean = true,
    val healthCalibration: HealthCalibrationMode = HealthCalibrationMode.AUTO_ADAPTIVE,
    val colorMode: Int = 1,
    val isPreset: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("gamingMode", gamingMode.name)
        put("touchGameMode", touchGameMode)
        put("touchSensitivity", touchSensitivity)
        put("schedBore", schedBore)
        put("schedBurstPenaltyScale", schedBurstPenaltyScale)
        put("schedBurstPenaltyOffset", schedBurstPenaltyOffset)
        put("schedBurstInheritType", schedBurstInheritType)
        put("dynamicFsync", dynamicFsync)
        put("fastCharge", fastCharge)
        put("healthCalibration", healthCalibration.name)
        put("colorMode", colorMode)
        put("isPreset", isPreset)
    }

    companion object {
        fun fromJson(json: JSONObject): CustomKprofile = CustomKprofile(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Custom Kprofile"),
            description = json.optString("description", ""),
            gamingMode = runCatching { GamingModeLevel.valueOf(json.optString("gamingMode", "BALANCED")) }.getOrDefault(GamingModeLevel.BALANCED),
            touchGameMode = json.optBoolean("touchGameMode", false),
            touchSensitivity = json.optInt("touchSensitivity", 0),
            schedBore = json.optBoolean("schedBore", true),
            schedBurstPenaltyScale = json.optInt("schedBurstPenaltyScale", 1536),
            schedBurstPenaltyOffset = json.optInt("schedBurstPenaltyOffset", 24),
            schedBurstInheritType = json.optInt("schedBurstInheritType", 2),
            dynamicFsync = json.optBoolean("dynamicFsync", true),
            fastCharge = json.optBoolean("fastCharge", true),
            healthCalibration = runCatching { HealthCalibrationMode.valueOf(json.optString("healthCalibration", "AUTO_ADAPTIVE")) }.getOrDefault(HealthCalibrationMode.AUTO_ADAPTIVE),
            colorMode = json.optInt("colorMode", 1),
            isPreset = json.optBoolean("isPreset", false)
        )

        val DEFAULT_PRESETS = listOf(
            CustomKprofile(
                id = "preset_esports_extreme",
                name = "eSports Low-Latency",
                description = "Maximum OPP boost floor, 240Hz touch polling, and aggressive BORE burst response",
                gamingMode = GamingModeLevel.EXTREME,
                touchGameMode = true,
                touchSensitivity = 3,
                schedBore = true,
                schedBurstPenaltyScale = 512,
                schedBurstPenaltyOffset = 8,
                schedBurstInheritType = 2,
                dynamicFsync = false,
                fastCharge = true,
                healthCalibration = HealthCalibrationMode.PERF_GUARD,
                colorMode = 2,
                isPreset = true
            ),
            CustomKprofile(
                id = "preset_marathon_battery",
                name = "Marathon Endurance SOT",
                description = "Powersave governor clamp, relaxed BORE penalty, and low power touch for maximum battery life",
                gamingMode = GamingModeLevel.POWERSAVE,
                touchGameMode = false,
                touchSensitivity = 0,
                schedBore = true,
                schedBurstPenaltyScale = 2048,
                schedBurstPenaltyOffset = 32,
                schedBurstInheritType = 1,
                dynamicFsync = true,
                fastCharge = false,
                healthCalibration = HealthCalibrationMode.LONGEVITY,
                colorMode = 1,
                isPreset = true
            ),
            CustomKprofile(
                id = "preset_daily_driver",
                name = "Daily Balanced Plus",
                description = "Autonomous MediaTek perfmgr balance with adaptive silicon OPP table brownout guard",
                gamingMode = GamingModeLevel.BALANCED,
                touchGameMode = false,
                touchSensitivity = 1,
                schedBore = true,
                schedBurstPenaltyScale = 1536,
                schedBurstPenaltyOffset = 24,
                schedBurstInheritType = 2,
                dynamicFsync = true,
                fastCharge = true,
                healthCalibration = HealthCalibrationMode.AUTO_ADAPTIVE,
                colorMode = 1,
                isPreset = true
            )
        )
    }
}
