package com.pox.kernelmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseAsset(
    val name: String,
    val size: Long = 0,
    val browser_download_url: String,
    val digestSha256: String = ""
)

@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val assets: List<ReleaseAsset> = emptyList()
)

data class KernelPackageItem(
    val editionName: String,
    val zipName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val releaseTag: String,
    val isRecommendedForDevice: Boolean
)
