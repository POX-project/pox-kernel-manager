package com.pox.kernelmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.data.model.DeviceInfo
import com.pox.kernelmanager.data.model.HardwareStats
import com.pox.kernelmanager.data.repo.HardwareRepository
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.Job
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

    /**
     * The hardware polling loop, held so it can be stopped when the app goes to
     * the background or the Dashboard tab leaves the composition.
     */
    private var monitorJob: Job? = null

    init {
        refresh()
    }

    /**
     * Drop the cached probe and reload.
     *
     * Called on every resume because root can be granted while the app is in the
     * background; without this the denial from the previous foreground session
     * would still be in effect.
     */
    fun onAppForegrounded() {
        ShellUtils.invalidateCache()
        refresh()
        startHardwareMonitorLoop()
    }

    /**
     * Cancel the 1.5 s polling loop. Called on ON_PAUSE and when the Dashboard
     * composable leaves the composition, so no shell round-trip happens while
     * the app is invisible.
     */
    fun onAppBackgrounded() {
        stopHardwareMonitorLoop()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val info = HardwareRepository.getDeviceInfo()
            val stats = HardwareRepository.getHardwareStats()
            _uiState.value = _uiState.value.copy(
                deviceInfo = info,
                stats = stats,
                isLoading = false,
                // Whether *this app* holds root, not whether some framework is installed.
                isRootGranted = info.rootGranted
            )
        }
    }

    fun startHardwareMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    val stats = HardwareRepository.getHardwareStats()
                    _uiState.value = _uiState.value.copy(stats = stats)
                } catch (_: Exception) { }
            }
        }
    }

    private fun stopHardwareMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}