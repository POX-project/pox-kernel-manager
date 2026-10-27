package com.pox.kernelmanager.data.model

import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class ContentReady(val items: List<UpdateItem>) : UpdateState
    data class UpdateAvailable(val release: GithubRelease, val packages: List<KernelPackageItem>) : UpdateState

    data class Downloading(
        val packageName: String,
        val progressPercent: Int,
        val downloadedMb: Float,
        val totalMb: Float,
        val speedKbps: Float
    ) : UpdateState

    data class DownloadCompleted(
        val file: File,
        val savedLocation: String,
        val item: UpdateItem? = null
    ) : UpdateState

    data class Flashing(
        val currentStep: String,
        val logs: List<String> = emptyList()
    ) : UpdateState

    data class FlashSuccess(
        val message: String,
        val logs: List<String> = emptyList()
    ) : UpdateState

    data class FlashFailed(
        val error: String,
        val logs: List<String> = emptyList()
    ) : UpdateState

    data class AppInstallReady(
        val apkFile: File,
        val isSilentSupported: Boolean = false
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}
