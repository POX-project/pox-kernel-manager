package com.pox.kernelmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.KprofilesLevel
import com.pox.kernelmanager.data.model.HealthCalibrationMode
import com.pox.kernelmanager.data.model.TuningFeature
import com.pox.kernelmanager.data.model.TuningFeatures
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.pox.kernelmanager.data.model.CustomColorProfile
import com.pox.kernelmanager.data.model.CustomKprofile
import com.pox.kernelmanager.data.repo.CustomProfilesRepository

data class FeaturesUiState(
    val features: TuningFeatures = TuningFeatures(),
    val isRootGranted: Boolean = false,
    val isLoading: Boolean = true,
    val feedbackMessage: String? = null,
    val customColorProfiles: List<CustomColorProfile> = emptyList(),
    val customKprofiles: List<CustomKprofile> = emptyList()
)

class FeaturesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FeaturesUiState())
    val uiState: StateFlow<FeaturesUiState> = _uiState.asStateFlow()

    init {
        loadFeatures()
    }

    fun onAppForegrounded() {
        ShellUtils.invalidateCache()
        com.pox.kernelmanager.data.root.KernelNodes.invalidate()
        loadFeatures()
    }

    fun refresh() = loadFeatures()

    fun loadFeatures() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val isRoot = ShellUtils.isRootAvailable()
            val feat = TuningRepository.getTuningFeatures()
            val colorProfiles = CustomProfilesRepository.getColorProfiles()
            val kprofiles = CustomProfilesRepository.getKprofiles()
            _uiState.value = FeaturesUiState(
                features = feat,
                isRootGranted = isRoot,
                isLoading = false,
                customColorProfiles = colorProfiles,
                customKprofiles = kprofiles
            )
        }
    }

    /**
     * Apply a change and then re-read the kernel.
     *
     * The previous version copied the requested value into the UI state
     * optimistically, so a rejected write still rendered as if it had been
     * applied. Re-reading means the switches always show what the kernel reports.
     */
    private fun apply(block: suspend () -> com.pox.kernelmanager.data.repo.TuningApplyResult) {
        viewModelScope.launch {
            val result = block()
            val refreshed = TuningRepository.getTuningFeatures()
            _uiState.value = _uiState.value.copy(
                features = refreshed,
                feedbackMessage = result.message
            )
        }
    }

    fun setGamingMode(level: GamingModeLevel) = apply { TuningRepository.setGamingMode(level) }

    fun setKprofilesMode(level: KprofilesLevel) = apply { TuningRepository.setKprofilesMode(level) }

    fun setAutoKp(enable: Boolean) = apply { TuningRepository.setAutoKp(enable) }

    fun setHealthCalibrationMode(mode: HealthCalibrationMode) =
        apply { TuningRepository.setHealthCalibrationMode(mode) }

    fun setOppVoltageGuard(enable: Boolean) = apply { TuningRepository.setOppVoltageGuard(enable) }

    fun setSchedBore(enable: Boolean) = apply { TuningRepository.setSchedBore(enable) }

    fun setSchedBurstPenaltyScale(scale: Int) =
        apply { TuningRepository.setSchedBurstPenaltyScale(scale) }

    fun setSchedBurstPenaltyOffset(offset: Int) =
        apply { TuningRepository.setSchedBurstPenaltyOffset(offset) }

    fun setSchedBurstInheritType(type: Int) =
        apply { TuningRepository.setSchedBurstInheritType(type) }

    fun setTouchBoost(enable: Boolean) = apply { TuningRepository.setTouchBoost(enable) }

fun setFpsCap(fps: Int) = apply { TuningRepository.setFpsCap(fps) }

fun setIoScheduler(scheduler: String) = apply { TuningRepository.setIoScheduler(scheduler) }

fun setCpuGovernor(governor: String) = apply { TuningRepository.setCpuGovernor(governor) }

fun setZramSizeMb(sizeMb: Long) = apply { TuningRepository.setZramSizeMb(sizeMb) }

    fun toggleFastCharge(enable: Boolean) = apply { TuningRepository.setFastCharge(enable) }

    fun setChargeWatt(watt: Int) = apply { TuningRepository.setChargeWatt(watt) }

    fun setPdMinWatt(watt: Int) = apply { TuningRepository.setPdMinWatt(watt) }

    fun setPdMaxWatt(watt: Int) = apply { TuningRepository.setPdMaxWatt(watt) }

    fun toggleBypassCharging(enable: Boolean) = apply { TuningRepository.setBypassCharging(enable) }

    fun setChargeLimit(limitPercent: Int) = apply { TuningRepository.setChargeLimit(limitPercent) }

    fun toggleThermalGuard(enable: Boolean, tempLimit: Int) =
        apply { TuningRepository.setThermalGuard(enable, tempLimit) }

    fun setTouchSensitivity(level: Int) = apply { TuningRepository.setTouchSensitivity(level) }

    fun setColorMode(mode: Int) = apply { TuningRepository.setColorMode(mode) }

    fun setHbmMode(level: Int) = apply { TuningRepository.setHbmMode(level) }

    fun setHeadphoneGain(gainDb: Int) = apply { TuningRepository.setHeadphoneGain(gainDb) }

    fun setMicGain(level: Int) = apply { TuningRepository.setMicGain(level) }

    fun setActiveMic(mode: Int) = apply { TuningRepository.setActiveMic(mode) }

    fun toggleWakelockBlocker(enable: Boolean) = apply { TuningRepository.setWakelockBlocker(enable) }

    fun toggleXswapd(enable: Boolean) = apply { TuningRepository.setXswapdEnabled(enable) }

    fun triggerXswapdReclaim() = apply { TuningRepository.triggerXswapdReclaim() }

    fun toggleDt2w(enable: Boolean) = apply { TuningRepository.setDoubleTapToWake(enable) }

    fun toggleTrueTone(enable: Boolean) = apply { TuningRepository.setTrueTone(enable) }

    fun setVibratorStrength(percent: Int) = apply { TuningRepository.setVibratorStrength(percent) }

    fun toggleDynamicFsync(enable: Boolean) = apply { TuningRepository.setDynamicFsync(enable) }

    fun toggleDriveDroidCdrom(enable: Boolean) = apply { TuningRepository.setDriveDroidCdrom(enable) }

    fun setReadAhead(kb: Int) = apply { TuningRepository.setReadAheadKb(kb) }

    fun toggleCamera4k60(enable: Boolean) = apply { TuningRepository.setCamera4k60(enable) }

    fun toggleSlog3(enable: Boolean) = apply { TuningRepository.setSlog3(enable) }

    fun setTorchBrightness(level: Int) = apply { TuningRepository.setTorchBrightness(level) }

    fun setTcpCongestion(algorithm: String) = apply { TuningRepository.setTcpCongestion(algorithm) }

    fun toggleTtl64(enable: Boolean) = apply { TuningRepository.setTtl64(enable) }

    fun toggleWqPowerEfficient(enable: Boolean) = apply { TuningRepository.setWqPowerEfficient(enable) }

    fun toggleWaltCpuUtil(enable: Boolean) = apply { TuningRepository.setWaltCpuUtil(enable) }

    fun toggleWaltTaskUtil(enable: Boolean) = apply { TuningRepository.setWaltTaskUtil(enable) }

    fun toggleSchedCstateAware(enable: Boolean) = apply { TuningRepository.setSchedCstateAware(enable) }

    fun setVmSwappiness(value: Int) = apply { TuningRepository.setVmSwappiness(value) }

    fun isSupported(feature: TuningFeature): Boolean = _uiState.value.features.isSupported(feature)

    fun applyCustomColorProfile(profile: CustomColorProfile) {
        apply { CustomProfilesRepository.applyColorProfile(profile) }
        refreshProfilesList()
    }

    fun saveCustomColorProfile(profile: CustomColorProfile) {
        CustomProfilesRepository.saveColorProfile(profile)
        refreshProfilesList()
    }

    fun deleteCustomColorProfile(id: String) {
        CustomProfilesRepository.deleteColorProfile(id)
        refreshProfilesList()
    }

    fun applyCustomKprofile(profile: CustomKprofile) {
        apply { CustomProfilesRepository.applyKprofile(profile) }
        refreshProfilesList()
    }

    fun saveCustomKprofile(profile: CustomKprofile) {
        CustomProfilesRepository.saveKprofile(profile)
        refreshProfilesList()
    }

    fun deleteCustomKprofile(id: String) {
        CustomProfilesRepository.deleteKprofile(id)
        refreshProfilesList()
    }

    private fun refreshProfilesList() {
        _uiState.value = _uiState.value.copy(
            customColorProfiles = CustomProfilesRepository.getColorProfiles(),
            customKprofiles = CustomProfilesRepository.getKprofiles()
        )
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }
}