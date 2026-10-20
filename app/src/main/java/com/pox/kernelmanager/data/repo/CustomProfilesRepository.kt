package com.pox.kernelmanager.data.repo

import android.content.Context
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.CustomColorProfile
import com.pox.kernelmanager.data.model.CustomKprofile
import com.pox.kernelmanager.data.model.KprofilesLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object CustomProfilesRepository {
    private val prefs by lazy {
        PoxApplication.instance.getSharedPreferences("pox_custom_profiles", Context.MODE_PRIVATE)
    }

    private const val KEY_COLOR_PROFILES = "custom_color_profiles_json"
    private const val KEY_KPROFILES = "custom_kprofiles_json"
    private const val KEY_ACTIVE_COLOR_ID = "active_color_profile_id"
    private const val KEY_ACTIVE_KPROFILE_ID = "active_kprofile_id"

    fun getColorProfiles(): List<CustomColorProfile> {
        val savedJson = prefs.getString(KEY_COLOR_PROFILES, null)
        val list = mutableListOf<CustomColorProfile>()
        list.addAll(CustomColorProfile.DEFAULT_PRESETS)
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    list.add(CustomColorProfile.fromJson(array.getJSONObject(i)))
                }
            }
        }
        return list
    }

    fun saveColorProfile(profile: CustomColorProfile) {
        val current = getColorProfiles().filter { !it.isPreset }.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_COLOR_PROFILES, array.toString()).apply()
    }

    fun deleteColorProfile(id: String) {
        val current = getColorProfiles().filter { !it.isPreset && it.id != id }
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_COLOR_PROFILES, array.toString()).apply()
    }

    fun getActiveColorProfileId(): String? = prefs.getString(KEY_ACTIVE_COLOR_ID, null)

    fun setActiveColorProfileId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_COLOR_ID, id).apply()
    }

    fun getKprofiles(): List<CustomKprofile> {
        val savedJson = prefs.getString(KEY_KPROFILES, null)
        val list = mutableListOf<CustomKprofile>()
        list.addAll(CustomKprofile.DEFAULT_PRESETS)
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    list.add(CustomKprofile.fromJson(array.getJSONObject(i)))
                }
            }
        }
        return list
    }

    fun saveKprofile(profile: CustomKprofile) {
        val current = getKprofiles().filter { !it.isPreset }.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_KPROFILES, array.toString()).apply()
    }

    fun deleteKprofile(id: String) {
        val current = getKprofiles().filter { !it.isPreset && it.id != id }
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_KPROFILES, array.toString()).apply()
    }

    fun getActiveKprofileId(): String? = prefs.getString(KEY_ACTIVE_KPROFILE_ID, null)

    fun setActiveKprofileId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_KPROFILE_ID, id).apply()
    }

    suspend fun applyColorProfile(profile: CustomColorProfile): TuningApplyResult = withContext(Dispatchers.IO) {
        setActiveColorProfileId(profile.id)
        TuningRepository.setColorCustom(
            sat = profile.saturation,
            contrast = profile.contrast,
            brightness = profile.brightness,
            satAdj = profile.saturationAdjustment
        )
    }

    suspend fun applyKprofile(profile: CustomKprofile): TuningApplyResult = withContext(Dispatchers.IO) {
        setActiveKprofileId(profile.id)
        // 1. Set Gaming Mode & Kprofiles Level to Custom
        // 2. The kernel custom profile (kp_mode 4) applies the 10 tunables
        //    from custom_params, so push them BEFORE activating mode 4 —
        //    otherwise "Custom" would silently apply builtin defaults.
        //
        // Field order MUST match gaming_mode.c kprofiles_custom_proc_show:
        // up_little_us down_little_us up_big_us down_big_us top_app_boost
        // fg_boost gpu_boost dram_boost touch_boost rescue_opp
        val customParams = listOf(
            profile.schedutilUpLittleUs,
            profile.schedutilDownLittleUs,
            profile.schedutilUpBigUs,
            profile.schedutilDownBigUs,
            profile.topAppBoost,
            profile.fgBoost,
            if (profile.gpuBoost) 1 else 0,
            if (profile.dramBoost) 1 else 0,
            if (profile.touchGameMode) 1 else 0,
            profile.rescueOpp
        ).joinToString(" ")

        val steps = listOf(
            TuningRepository.setGamingMode(profile.gamingMode),
            TuningRepository.setKprofilesCustomParams(customParams),
            TuningRepository.setKprofilesMode(KprofilesLevel.CUSTOM),
            // 3. Set Touch
            TuningRepository.setTouchBoost(profile.touchGameMode),
            TuningRepository.setTouchSensitivity(profile.touchSensitivity),
            // 4. Set BORE Scheduler
            TuningRepository.setSchedBore(profile.schedBore),
            TuningRepository.setSchedBurstPenaltyScale(profile.schedBurstPenaltyScale),
            TuningRepository.setSchedBurstPenaltyOffset(profile.schedBurstPenaltyOffset),
            TuningRepository.setSchedBurstInheritType(profile.schedBurstInheritType),
            // 5. Set Hardware Features
            TuningRepository.setDynamicFsync(profile.dynamicFsync),
            TuningRepository.setFastCharge(profile.fastCharge),
            TuningRepository.setHealthCalibrationMode(profile.healthCalibration),
            TuningRepository.setColorMode(profile.colorMode)
        )

        // The kernel write chain reports per-step truth; a profile that cannot
        // reach the kernel must not claim a successful hardware apply.
        val failed = steps.count { !it.isHardwareApplied }
        TuningApplyResult(
            isHardwareApplied = failed == 0,
            message = if (failed == 0) "Profile '${profile.name}' applied successfully"
            else "Profile '${profile.name}' applied with $failed of ${steps.size} kernel writes rejected - check root access and kernel build"
        )
    }
}
