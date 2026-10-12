package com.pox.kernelmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.data.model.DeviceInfo
import com.pox.kernelmanager.data.model.HardwareStats
import com.pox.kernelmanager.data.repo.HardwareRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardUiState(
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val stats: HardwareStats = HardwareStats(),
    val isLoading: Boolean = true,
    val isRootGranted: Boolean = false
)

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        startHardwareMonitorLoop()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val info = HardwareRepository.getDeviceInfo()
            val stats = HardwareRepository.getHardwareStats()
            _uiState.value = DashboardUiState(
                deviceInfo = info,
                stats = stats,
                isLoading = false,
                isRootGranted = info.rootProvider != com.pox.kernelmanager.data.model.RootProvider.ROOTLESS
            )
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val info = HardwareRepository.getDeviceInfo()
            val stats = HardwareRepository.getHardwareStats()
            _uiState.value = DashboardUiState(
                deviceInfo = info,
                stats = stats,
                isLoading = false,
                isRootGranted = info.rootProvider != com.pox.kernelmanager.data.model.RootProvider.ROOTLESS
            )
        }
    }

    private fun startHardwareMonitorLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(1500)
                try {
                    val stats = HardwareRepository.getHardwareStats()
                    _uiState.value = _uiState.value.copy(stats = stats)
                } catch (_: Exception) { }
            }
        }
    }
}
