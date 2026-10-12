package com.pox.kernelmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.TuningFeatures
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeaturesUiState(
    val features: TuningFeatures = TuningFeatures(),
    val isRootGranted: Boolean = false,
    val isLoading: Boolean = true,
    val feedbackMessage: String? = null
)

class FeaturesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FeaturesUiState())
    val uiState: StateFlow<FeaturesUiState> = _uiState.asStateFlow()

    init {
        loadFeatures()
    }

    fun loadFeatures() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val isRoot = ShellUtils.isRootAvailable()
            val feat = TuningRepository.getTuningFeatures()
            _uiState.value = FeaturesUiState(
                features = feat,
                isRootGranted = isRoot,
                isLoading = false
            )
        }
    }

    fun setGamingMode(level: GamingModeLevel) {
        viewModelScope.launch {
            val result = TuningRepository.setGamingMode(level)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(gamingMode = level),
                feedbackMessage = result.message
            )
        }
    }

    fun toggleBypassCharging(enable: Boolean) {
        viewModelScope.launch {
            val result = TuningRepository.setBypassCharging(enable)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(bypassCharging = enable),
                feedbackMessage = result.message
            )
        }
    }

    fun setChargeLimit(limitPercent: Int) {
        viewModelScope.launch {
            val result = TuningRepository.setChargeLimit(limitPercent)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(chargeLimitPercent = limitPercent),
                feedbackMessage = result.message
            )
        }
    }

    fun toggleThermalGuard(enable: Boolean, tempLimit: Int) {
        viewModelScope.launch {
            val result = TuningRepository.setThermalGuard(enable, tempLimit)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(
                    thermalGuardEnabled = enable,
                    tempLimitC = tempLimit
                ),
                feedbackMessage = result.message
            )
        }
    }

    fun toggleXswapd(enable: Boolean) {
        viewModelScope.launch {
            val result = TuningRepository.setXswapdEnabled(enable)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(xswapdEnabled = enable),
                feedbackMessage = result.message
            )
        }
    }

    fun triggerXswapdReclaim() {
        viewModelScope.launch {
            val result = TuningRepository.triggerXswapdReclaim()
            _uiState.value = _uiState.value.copy(feedbackMessage = result.message)
        }
    }

    fun toggleDt2w(enable: Boolean) {
        viewModelScope.launch {
            val result = TuningRepository.setDoubleTapToWake(enable)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(doubleTapToWake = enable),
                feedbackMessage = result.message
            )
        }
    }

    fun toggleTrueTone(enable: Boolean) {
        viewModelScope.launch {
            val result = TuningRepository.setTrueTone(enable)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(trueToneDisplay = enable),
                feedbackMessage = result.message
            )
        }
    }

    fun setVibratorStrength(percent: Int) {
        viewModelScope.launch {
            val result = TuningRepository.setVibratorStrength(percent)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(vibratorStrengthPercent = percent),
                feedbackMessage = result.message
            )
        }
    }

    fun toggleDynamicFsync(enable: Boolean) {
        viewModelScope.launch {
            val result = TuningRepository.setDynamicFsync(enable)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(dynamicFsync = enable),
                feedbackMessage = result.message
            )
        }
    }

    fun setReadAhead(kb: Int) {
        viewModelScope.launch {
            val result = TuningRepository.setReadAheadKb(kb)
            _uiState.value = _uiState.value.copy(
                features = _uiState.value.features.copy(readAheadKb = kb),
                feedbackMessage = result.message
            )
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }
}
