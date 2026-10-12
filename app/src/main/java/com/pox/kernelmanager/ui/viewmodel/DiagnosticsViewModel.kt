package com.pox.kernelmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticsUiState(
    val dmesgLogs: List<String> = emptyList(),
    val selinuxMode: String = "Enforcing",
    val isRootGranted: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null
)

class DiagnosticsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        loadDiagnostics()
    }

    fun loadDiagnostics() {
        viewModelScope.launch {
            val root = ShellUtils.isRootAvailable()
            val selinux = ShellUtils.detectSELinux()
            val dmesg = if (root) {
                ShellUtils.runCommand("dmesg | tail -n 80").output
            } else {
                listOf("Root access required to view full dmesg buffer.")
            }
            _uiState.value = DiagnosticsUiState(
                dmesgLogs = dmesg,
                selinuxMode = selinux,
                isRootGranted = root
            )
        }
    }

    fun toggleSELinux() {
        viewModelScope.launch {
            val current = _uiState.value.selinuxMode
            val target = if (current.equals("Enforcing", true)) "0" else "1"
            ShellUtils.runCommand("setenforce $target")
            val updated = ShellUtils.detectSELinux()
            _uiState.value = _uiState.value.copy(
                selinuxMode = updated,
                exportMessage = "SELinux set to $updated"
            )
        }
    }

    fun exportFullReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val outFile = File("/sdcard/Download/pox_report_$timestamp.txt")

                val dmesg = ShellUtils.runCommand("dmesg").output.joinToString("\n")
                val uname = ShellUtils.runCommand("uname -a").output.joinToString("\n")
                val mounts = ShellUtils.fastRead("/proc/mounts") ?: ""
                val meminfo = ShellUtils.fastRead("/proc/meminfo") ?: ""

                val report = buildString {
                    appendLine("==========================================")
                    appendLine("POX KERNEL MANAGER DIAGNOSTIC REPORT 🇵🇸")
                    appendLine("Date: ${Date()}")
                    appendLine("Device: Redmi Note 8 Pro (begonia / MT6785)")
                    appendLine("Uname: $uname")
                    appendLine("SELinux: ${ShellUtils.detectSELinux()}")
                    appendLine("Root: ${ShellUtils.detectRootProvider().displayName}")
                    appendLine("NoMount: ${ShellUtils.detectNoMount()}")
                    appendLine("==========================================")
                    appendLine("\n[MEMINFO]\n$meminfo")
                    appendLine("\n[MOUNTS]\n$mounts")
                    appendLine("\n[DMESG]\n$dmesg")
                }

                outFile.parentFile?.mkdirs()
                outFile.writeText(report)

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = "Saved report to ${outFile.absolutePath}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = "Export failed: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }
}
