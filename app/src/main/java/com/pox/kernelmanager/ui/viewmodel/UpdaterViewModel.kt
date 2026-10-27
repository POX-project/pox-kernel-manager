package com.pox.kernelmanager.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pox.kernelmanager.BuildConfig
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.*
import com.pox.kernelmanager.data.repo.DownloadsStore
import com.pox.kernelmanager.data.repo.HardwareRepository
import com.pox.kernelmanager.data.repo.KernelUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class UpdaterViewModel : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(UpdateCategory.KERNEL)
    val selectedCategory: StateFlow<UpdateCategory> = _selectedCategory.asStateFlow()

    private val _selectedChannel = MutableStateFlow(ReleaseChannel.STABLE_RELEASES)
    val selectedChannel: StateFlow<ReleaseChannel> = _selectedChannel.asStateFlow()

    private val _updateItems = MutableStateFlow<List<UpdateItem>>(emptyList())
    val updateItems: StateFlow<List<UpdateItem>> = _updateItems.asStateFlow()

    private val _selectedItem = MutableStateFlow<UpdateItem?>(null)
    val selectedItem: StateFlow<UpdateItem?> = _selectedItem.asStateFlow()

    // Terminal Logging State for in-app KernelFlasher
    private val _flashLogs = MutableStateFlow<List<String>>(emptyList())
    val flashLogs: StateFlow<List<String>> = _flashLogs.asStateFlow()

    private val _isFlashingActive = MutableStateFlow(false)
    val isFlashingActive: StateFlow<Boolean> = _isFlashingActive.asStateFlow()

    private val _flashingSuccess = MutableStateFlow<Boolean?>(null)
    val flashingSuccess: StateFlow<Boolean?> = _flashingSuccess.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    val currentAppVersion: String = BuildConfig.VERSION_NAME

    // Backwards-compatibility
    private val _availablePackages = MutableStateFlow<List<KernelPackageItem>>(emptyList())
    val availablePackages: StateFlow<List<KernelPackageItem>> = _availablePackages.asStateFlow()

    private val _selectedPackage = MutableStateFlow<KernelPackageItem?>(null)
    val selectedPackage: StateFlow<KernelPackageItem?> = _selectedPackage.asStateFlow()

    private val _latestRelease = MutableStateFlow<GithubRelease?>(null)
    val latestRelease: StateFlow<GithubRelease?> = _latestRelease.asStateFlow()

    init {
        checkForUpdates()
    }

    fun setCategory(category: UpdateCategory) {
        if (_selectedCategory.value != category) {
            _selectedCategory.value = category
            _selectedItem.value = null
            checkForUpdates()
        }
    }

    fun setChannel(channel: ReleaseChannel) {
        if (_selectedChannel.value != channel) {
            _selectedChannel.value = channel
            _selectedItem.value = null
            checkForUpdates()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val devInfo = HardwareRepository.getDeviceInfo()
                _deviceInfo.value = devInfo

                val category = _selectedCategory.value
                val channel = _selectedChannel.value

                val items = when (category) {
                    UpdateCategory.KERNEL -> {
                        when (channel) {
                            ReleaseChannel.STABLE_RELEASES -> {
                                val releases = KernelUpdateRepository.fetchLatestReleases()
                                val latest = releases.firstOrNull()
                                if (latest != null) {
                                    _latestRelease.value = latest
                                    val pkgs = KernelUpdateRepository.parsePackagesFromRelease(latest, devInfo.kernelEdition)
                                    _availablePackages.value = pkgs
                                    _selectedPackage.value = pkgs.firstOrNull { it.isRecommendedForDevice } ?: pkgs.firstOrNull()
                                }
                                KernelUpdateRepository.parseKernelReleaseItems(
                                    releases,
                                    devInfo.kernelEdition,
                                    devInfo.kernelRelease
                                )
                            }
                            ReleaseChannel.ACTIONS_BUILDS -> {
                                val runs = KernelUpdateRepository.fetchKernelActionRuns()
                                KernelUpdateRepository.parseKernelActionRunItems(runs, devInfo.kernelEdition)
                            }
                        }
                    }
                    UpdateCategory.APP_MANAGER -> {
                        when (channel) {
                            ReleaseChannel.STABLE_RELEASES -> {
                                val releases = KernelUpdateRepository.fetchManagerReleases()
                                KernelUpdateRepository.parseManagerReleaseItems(releases, currentAppVersion)
                            }
                            ReleaseChannel.ACTIONS_BUILDS -> {
                                val runs = KernelUpdateRepository.fetchManagerActionRuns()
                                KernelUpdateRepository.parseManagerActionRunItems(runs, currentAppVersion)
                            }
                        }
                    }
                }

                _updateItems.value = items
                val recommended = items.firstOrNull { it.isRecommended } ?: items.firstOrNull()
                _selectedItem.value = recommended

                _updateState.value = if (items.isNotEmpty()) {
                    UpdateState.ContentReady(items)
                } else {
                    UpdateState.Error("No items found for the selected category & channel.")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Failed to query updates from GitHub.")
            }
        }
    }

    fun selectItem(item: UpdateItem) {
        _selectedItem.value = item
    }

    fun selectPackage(pkg: KernelPackageItem) {
        _selectedPackage.value = pkg
    }

    fun downloadSelectedItem() {
        val item = _selectedItem.value ?: return
        viewModelScope.launch {
            try {
                val cacheFile = File(PoxApplication.instance.cacheDir, item.fileName)
                KernelUpdateRepository.downloadPackageWithProgress(item.downloadUrl, cacheFile)
                    .collect { progress ->
                        _updateState.value = UpdateState.Downloading(
                            packageName = item.fileName,
                            progressPercent = progress.percent,
                            downloadedMb = progress.downloadedMb,
                            totalMb = progress.totalMb,
                            speedKbps = progress.speedKbps
                        )
                    }

                // Verify SHA-256 if supplied
                if (item.sha256.isNotBlank()) {
                    val verified = KernelUpdateRepository.verifySha256(cacheFile, item.sha256)
                    if (!verified) {
                        cacheFile.delete()
                        _updateState.value = UpdateState.Error("SHA-256 integrity check failed!")
                        return@launch
                    }
                }

                val mime = if (item.fileName.endsWith(".apk")) "application/vnd.android.package-archive" else "application/zip"
                val location = DownloadsStore.write(
                    fileName = item.fileName,
                    mimeType = mime,
                    content = cacheFile.readBytes()
                )

                _updateState.value = UpdateState.DownloadCompleted(cacheFile, location, item)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Download error: ${e.message}")
            }
        }
    }

    /**
     * Direct in-app AnyKernel3 flash (KernelFlasher).
     */
    fun flashKernelDirectly(file: File) {
        viewModelScope.launch {
            _flashLogs.value = emptyList()
            _isFlashingActive.value = true
            _flashingSuccess.value = null
            _updateState.value = UpdateState.Flashing("Starting direct kernel flash...", emptyList())

            try {
                KernelUpdateRepository.flashKernelDirectly(file).collect { event ->
                    val updated = _flashLogs.value.toMutableList().apply { add(event.line) }
                    _flashLogs.value = updated
                    _updateState.value = UpdateState.Flashing(event.step, updated)

                    if (event.isFinished) {
                        _isFlashingActive.value = false
                        _flashingSuccess.value = event.isSuccess
                        if (event.isSuccess) {
                            _updateState.value = UpdateState.FlashSuccess(event.line, updated)
                        } else {
                            _updateState.value = UpdateState.FlashFailed(event.line, updated)
                        }
                    }
                }
            } catch (e: Exception) {
                _isFlashingActive.value = false
                _flashingSuccess.value = false
                val errorMsg = "Flashing error: ${e.message}"
                val updated = _flashLogs.value.toMutableList().apply { add(errorMsg) }
                _flashLogs.value = updated
                _updateState.value = UpdateState.FlashFailed(errorMsg, updated)
            }
        }
    }

    /**
     * Flashes any local ZIP file selected from storage by the user via file picker (KernelFlasher style).
     */
    fun flashLocalZip(uri: Uri, context: Context) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Flashing("Reading local ZIP archive...", emptyList())
            val tempZip = File(context.cacheDir, "local_kernel_${System.currentTimeMillis()}.zip")
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempZip).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("Cannot open selected file stream.")
                }
                flashKernelDirectly(tempZip)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Failed to import local file: ${e.message}")
            }
        }
    }

    /**
     * Installs downloaded companion app update via root silent or FileProvider intent.
     */
    fun installAppUpdate(file: File, context: Context) {
        viewModelScope.launch {
            try {
                val result = KernelUpdateRepository.installAppUpdate(context, file)
                _updateState.value = UpdateState.FlashSuccess(result)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("App installation failed: ${e.message}")
            }
        }
    }

    /**
     * Fallback recovery staging via OpenRecoveryScript.
     */
    fun stageDownloadedPackageForRecovery(file: File) {
        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Flashing("Staging for recovery flash...", emptyList())
                KernelUpdateRepository.flashViaOpenRecoveryScript(file).collect { step ->
                    _updateState.value = UpdateState.Flashing(step, listOf(step))
                }
                _updateState.value = UpdateState.FlashSuccess(
                    "Kernel package staged to /cache/recovery/openrecoveryscript. Reboot to recovery to install."
                )
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Staging failed: ${e.message}")
            }
        }
    }

    fun rebootToRecovery() {
        viewModelScope.launch {
            KernelUpdateRepository.rebootToRecovery()
        }
    }

    fun rebootSystem() {
        viewModelScope.launch {
            KernelUpdateRepository.rebootSystem()
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
        _isFlashingActive.value = false
        _flashingSuccess.value = null
    }

    fun clearFlashTerminal() {
        _flashLogs.value = emptyList()
        _isFlashingActive.value = false
        _flashingSuccess.value = null
        _updateState.value = UpdateState.Idle
    }
}
