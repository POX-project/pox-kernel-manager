package com.pox.kernelmanager.data.model

import kotlinx.serialization.Serializable

enum class ReleaseChannel(val title: String) {
    STABLE_RELEASES("Official Releases"),
    ACTIONS_BUILDS("CI Test Builds")
}

enum class UpdateCategory(val title: String) {
    KERNEL("Kernel"),
    APP_MANAGER("Pox Manager")
}

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

@Serializable
data class GithubActionRun(
    val id: Long,
    val name: String = "",
    val head_branch: String = "",
    val head_sha: String = "",
    val display_title: String = "",
    val run_number: Int = 0,
    val status: String = "",
    val conclusion: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val artifacts_url: String = ""
)

@Serializable
data class GithubRunsResponse(
    val total_count: Int = 0,
    val workflow_runs: List<GithubActionRun> = emptyList()
)

@Serializable
data class GithubArtifact(
    val id: Long,
    val name: String = "",
    val size_in_bytes: Long = 0,
    val archive_download_url: String = "",
    val expired: Boolean = false,
    val created_at: String = ""
)

@Serializable
data class GithubArtifactsResponse(
    val total_count: Int = 0,
    val artifacts: List<GithubArtifact> = emptyList()
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

data class UpdateItem(
    val id: String,
    val title: String,
    val tagOrRunNumber: String,
    val category: UpdateCategory,
    val channel: ReleaseChannel,
    val branch: String? = null,
    val commitSha: String? = null,
    val publishedAt: String? = null,
    val changelog: String? = null,
    val fileName: String,
    val fileSizeBytes: Long = 0,
    val downloadUrl: String,
    val isRecommended: Boolean = false,
    val isInstalledOrNewer: Boolean = false,
    val sha256: String = "",
    val editionName: String = "",
    val isArtifactZip: Boolean = false
)
