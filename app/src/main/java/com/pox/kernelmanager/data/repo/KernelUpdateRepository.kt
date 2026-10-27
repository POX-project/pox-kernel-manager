package com.pox.kernelmanager.data.repo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pox.kernelmanager.data.model.*
import com.pox.kernelmanager.data.root.ShellUtils
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

object KernelUpdateRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    const val KERNEL_REPO_PRIMARY = "POX-project/pox-begonia-kernel"
    const val KERNEL_REPO_FALLBACK = "txorav/pox-kernel-begonia"
    const val KERNEL_REPO_M20LTE = "txorav/kernel_samsung_universal7885"
    const val MANAGER_REPO = "POX-project/pox-kernel-manager"

    private const val GITHUB_API = "https://api.github.com/repos"

    private fun getKernelRepo(): String {
        val dev = android.os.Build.DEVICE ?: ""
        val model = android.os.Build.MODEL ?: ""
        return if (dev.contains("m20", true) || model.contains("M20", true)) {
            KERNEL_REPO_M20LTE
        } else {
            KERNEL_REPO_PRIMARY
        }
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PoxKernelManager-Android")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
            return response.body?.string() ?: throw Exception("Empty response")
        }
    }

    suspend fun fetchLatestReleases(): List<GithubRelease> = withContext(Dispatchers.IO) {
        val repo = getKernelRepo()
        val releasesJson = try {
            fetchUrl("$GITHUB_API/$repo/releases")
        } catch (_: Exception) {
            if (repo == KERNEL_REPO_PRIMARY) {
                fetchUrl("$GITHUB_API/$KERNEL_REPO_FALLBACK/releases")
            } else {
                fetchUrl("$GITHUB_API/$KERNEL_REPO_M20LTE/releases")
            }
        }
        json.decodeFromString<List<GithubRelease>>(releasesJson)
    }

    suspend fun fetchManagerReleases(): List<GithubRelease> = withContext(Dispatchers.IO) {
        val releasesJson = fetchUrl("$GITHUB_API/$MANAGER_REPO/releases")
        json.decodeFromString<List<GithubRelease>>(releasesJson)
    }

    suspend fun fetchKernelActionRuns(): List<GithubActionRun> = withContext(Dispatchers.IO) {
        val repo = getKernelRepo()
        val runsJson = try {
            fetchUrl("$GITHUB_API/$repo/actions/runs?status=success&per_page=12")
        } catch (_: Exception) {
            if (repo == KERNEL_REPO_PRIMARY) {
                fetchUrl("$GITHUB_API/$KERNEL_REPO_FALLBACK/actions/runs?status=success&per_page=12")
            } else {
                fetchUrl("$GITHUB_API/$KERNEL_REPO_M20LTE/actions/runs?status=success&per_page=12")
            }
        }
        val response = json.decodeFromString<GithubRunsResponse>(runsJson)
        response.workflow_runs
    }

    suspend fun fetchManagerActionRuns(): List<GithubActionRun> = withContext(Dispatchers.IO) {
        val runsJson = fetchUrl("$GITHUB_API/$MANAGER_REPO/actions/runs?status=success&per_page=12")
        val response = json.decodeFromString<GithubRunsResponse>(runsJson)
        response.workflow_runs
    }

    suspend fun fetchRunArtifacts(artifactsUrl: String): List<GithubArtifact> = withContext(Dispatchers.IO) {
        try {
            val artifactsJson = fetchUrl(artifactsUrl)
            val response = json.decodeFromString<GithubArtifactsResponse>(artifactsJson)
            response.artifacts.filter { !it.expired }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parsePackagesFromRelease(release: GithubRelease, currentEdition: String): List<KernelPackageItem> {
        val items = mutableListOf<KernelPackageItem>()
        for (asset in release.assets) {
            if (!asset.name.endsWith(".zip") || asset.name.contains("latest", true)) continue

            val edition = detectEditionName(asset.name)

            items.add(
                KernelPackageItem(
                    editionName = edition,
                    zipName = asset.name,
                    downloadUrl = asset.browser_download_url,
                    sizeBytes = asset.size,
                    sha256 = asset.digestSha256,
                    releaseTag = release.tag_name,
                    isRecommendedForDevice = edition.contains(currentEdition, true)
                )
            )
        }
        return items
    }

    fun parseKernelReleaseItems(
        releases: List<GithubRelease>,
        currentEdition: String,
        currentRelease: String
    ): List<UpdateItem> {
        val items = mutableListOf<UpdateItem>()
        for (release in releases) {
            for (asset in release.assets) {
                if (!asset.name.endsWith(".zip") || asset.name.contains("latest", true)) continue
                val edition = detectEditionName(asset.name)
                items.add(
                    UpdateItem(
                        id = "${release.tag_name}_${asset.name}",
                        title = "${release.name ?: release.tag_name} ($edition)",
                        tagOrRunNumber = release.tag_name,
                        category = UpdateCategory.KERNEL,
                        channel = ReleaseChannel.STABLE_RELEASES,
                        publishedAt = release.published_at,
                        changelog = release.body,
                        fileName = asset.name,
                        fileSizeBytes = asset.size,
                        downloadUrl = asset.browser_download_url,
                        isRecommended = edition.contains(currentEdition, true) || (currentEdition.isNotBlank() && currentEdition.contains(edition, true)),
                        isInstalledOrNewer = currentRelease.contains(release.tag_name, ignoreCase = true),
                        sha256 = asset.digestSha256,
                        editionName = edition,
                        isArtifactZip = false
                    )
                )
            }
        }
        return items
    }

    suspend fun parseKernelActionRunItems(
        runs: List<GithubActionRun>,
        currentEdition: String
    ): List<UpdateItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<UpdateItem>()
        for (run in runs) {
            val artifacts = fetchRunArtifacts(run.artifacts_url)
            for (art in artifacts) {
                if (!art.name.endsWith(".zip")) continue
                val edition = detectEditionName(art.name)
                val downloadUrl = "https://nightly.link/$KERNEL_REPO_PRIMARY/actions/runs/${run.id}/${art.name}"
                items.add(
                    UpdateItem(
                        id = "run_${run.id}_${art.id}",
                        title = "${run.display_title.ifBlank { run.name }} (#${run.run_number})",
                        tagOrRunNumber = "Run #${run.run_number}",
                        category = UpdateCategory.KERNEL,
                        channel = ReleaseChannel.ACTIONS_BUILDS,
                        branch = run.head_branch,
                        commitSha = run.head_sha.take(7),
                        publishedAt = run.created_at,
                        changelog = "Commit: ${run.head_sha}\nBranch: ${run.head_branch}\nWorkflow: ${run.name}",
                        fileName = art.name,
                        fileSizeBytes = art.size_in_bytes,
                        downloadUrl = downloadUrl,
                        isRecommended = edition.contains(currentEdition, true) || (currentEdition.isNotBlank() && currentEdition.contains(edition, true)),
                        editionName = edition,
                        isArtifactZip = true
                    )
                )
            }
        }
        items
    }

    fun parseManagerReleaseItems(
        releases: List<GithubRelease>,
        installedVersionName: String
    ): List<UpdateItem> {
        val items = mutableListOf<UpdateItem>()
        for (release in releases) {
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: continue
            val releaseVersion = release.tag_name.removePrefix("v").trim()
            val isNewer = isVersionNewer(releaseVersion, installedVersionName)
            items.add(
                UpdateItem(
                    id = "manager_rel_${release.tag_name}",
                    title = "Pox Kernel Manager ${release.tag_name}",
                    tagOrRunNumber = release.tag_name,
                    category = UpdateCategory.APP_MANAGER,
                    channel = ReleaseChannel.STABLE_RELEASES,
                    publishedAt = release.published_at,
                    changelog = release.body,
                    fileName = apkAsset.name,
                    fileSizeBytes = apkAsset.size,
                    downloadUrl = apkAsset.browser_download_url,
                    isRecommended = isNewer,
                    isInstalledOrNewer = !isNewer,
                    editionName = "Companion App (Release)"
                )
            )
        }
        return items
    }

    suspend fun parseManagerActionRunItems(
        runs: List<GithubActionRun>,
        installedVersionName: String
    ): List<UpdateItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<UpdateItem>()
        for (run in runs) {
            val artifacts = fetchRunArtifacts(run.artifacts_url)
            for (art in artifacts) {
                val isApkOrZip = art.name.contains("apk", true) || art.name.contains("app", true)
                if (!isApkOrZip) continue

                val downloadUrl = "https://nightly.link/$MANAGER_REPO/actions/runs/${run.id}/${art.name}.zip"
                items.add(
                    UpdateItem(
                        id = "manager_run_${run.id}_${art.id}",
                        title = "${run.display_title.ifBlank { "CI Build" }} (#${run.run_number})",
                        tagOrRunNumber = "Run #${run.run_number}",
                        category = UpdateCategory.APP_MANAGER,
                        channel = ReleaseChannel.ACTIONS_BUILDS,
                        branch = run.head_branch,
                        commitSha = run.head_sha.take(7),
                        publishedAt = run.created_at,
                        changelog = "Commit: ${run.head_sha}\nBranch: ${run.head_branch}\nAutomated test build from GitHub Actions.",
                        fileName = "${art.name}.zip",
                        fileSizeBytes = art.size_in_bytes,
                        downloadUrl = downloadUrl,
                        isRecommended = true,
                        editionName = "CI Test Build",
                        isArtifactZip = true
                    )
                )
            }
        }
        items
    }

    private fun detectEditionName(filename: String): String = when {
        filename.contains("Onyx-Sultan-APatch", true) -> "Onyx-Sultan-APatch"
        filename.contains("Onyx-Sultan", true) -> "Onyx-Sultan"
        filename.contains("Onyx-KSU-Next", true) -> "Onyx-KSU-Next"
        filename.contains("Onyx-APatch", true) -> "Onyx-APatch"
        filename.contains("Onyx-BakaSU", true) -> "Onyx-BakaSU"
        filename.contains("Onyx-ReSukiSU", true) -> "Onyx-ReSukiSU"
        filename.contains("Onyx-Kaeru", true) -> "Onyx-Kaeru"
        filename.contains("Onyx", true) -> "Onyx (Clean Unrooted)"
        filename.contains("Granite", true) -> "Granite (LTS)"
        filename.contains("Obsidian", true) -> "Obsidian (iOS RAM)"
        else -> "Custom Edition"
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote == local) return false
        val rParts = remote.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun downloadPackageWithProgress(
        downloadUrl: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "PoxKernelManager-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed with HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty download body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            destinationFile.parentFile?.mkdirs()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastEmitTime = System.currentTimeMillis()
            var bytesSinceLastEmit = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastEmit += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastEmitTime >= 300 || downloadedBytes == totalBytes) {
                    val durationSec = (now - lastEmitTime) / 1000f
                    val speedKbps = if (durationSec > 0) (bytesSinceLastEmit / 1024f) / durationSec else 0f
                    val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                    emit(
                        DownloadProgress(
                            percent = percent,
                            downloadedMb = downloadedBytes / (1024f * 1024f),
                            totalMb = totalBytes / (1024f * 1024f),
                            speedKbps = speedKbps
                        )
                    )
                    lastEmitTime = now
                    bytesSinceLastEmit = 0L
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun verifySha256(file: File, expectedHash: String): Boolean = withContext(Dispatchers.IO) {
        if (expectedHash.isEmpty()) return@withContext true
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            hex.equals(expectedHash, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * KernelFlasher-style in-app direct AnyKernel3 flashing.
     * Backs up the current active boot partition to `/sdcard/PoxKernel/` and `/data/local/tmp/`,
     * unpacks AnyKernel3, and executes `META-INF/com/google/android/update-binary 3 1 "$ZIP"`,
     * streaming stdout and stderr live to the UI terminal.
     */
    fun flashKernelDirectly(zipFile: File): Flow<FlashProgressEvent> = callbackFlow {
        withContext(Dispatchers.IO) {
            send(FlashProgressEvent("Environment Check", "Checking root privileges..."))
            val env = ShellUtils.environment()
            if (!env.granted) {
                send(FlashProgressEvent("Aborted", "Root access denied! Cannot flash kernel directly.", isFinished = true, isSuccess = false))
                close()
                return@withContext
            }
            send(FlashProgressEvent("Environment Check", "Root environment verified (${env.provider.displayName})."))

            // 1. Boot Partition Backup
            send(FlashProgressEvent("Partition Backup", "Creating failsafe boot partition backup..."))
            val backupTmp = "/data/local/tmp/pox_boot_backup.img"
            val backupSd = "/sdcard/PoxKernel/boot_backup_${System.currentTimeMillis()}.img"
            ShellUtils.runCommand("mkdir -p /sdcard/PoxKernel /data/local/tmp/pox_flasher/ak3")
            val backupRes = ShellUtils.runCommand("dd if=/dev/block/by-name/boot of=$backupTmp bs=4M 2>/dev/null && sync")
            if (backupRes.isSuccess) {
                ShellUtils.runCommand("cp $backupTmp $backupSd 2>/dev/null")
                send(FlashProgressEvent("Partition Backup", "Boot backup saved to $backupSd"))
            } else {
                send(FlashProgressEvent("Partition Backup", "Warning: boot backup failed, proceeding with caution..."))
            }

            // 2. Extract Package
            send(FlashProgressEvent("Staging Package", "Unpacking AnyKernel3 payload to /data/local/tmp/pox_flasher/ak3..."))
            val stageZip = "/data/local/tmp/pox_flasher/package.zip"
            ShellUtils.runCommand("cp '${zipFile.absolutePath}' '$stageZip' && sync")

            // Unpack via root shell or Java fallback
            ShellUtils.runCommand("rm -rf /data/local/tmp/pox_flasher/ak3/*")
            val unzipRes = ShellUtils.runCommand("unzip -o '$stageZip' -d /data/local/tmp/pox_flasher/ak3/")
            if (!unzipRes.isSuccess) {
                try {
                    unzipArchive(zipFile, File("/data/local/tmp/pox_flasher/ak3"))
                } catch (e: Exception) {
                    send(FlashProgressEvent("Extraction Error", "Failed to unzip package: ${e.message}", isFinished = true, isSuccess = false))
                    close()
                    return@withContext
                }
            }

            // Check if there is an inner zip (e.g. actions artifact container)
            val innerZipCheck = ShellUtils.runCommand("ls /data/local/tmp/pox_flasher/ak3/*.zip 2>/dev/null | head -n 1")
            val innerZip = innerZipCheck.output.firstOrNull()?.trim()
            if (!innerZip.isNullOrEmpty() && innerZip.endsWith(".zip")) {
                send(FlashProgressEvent("Unpacking Nested Artifact", "Unpacking inner artifact $innerZip..."))
                ShellUtils.runCommand("unzip -o '$innerZip' -d /data/local/tmp/pox_flasher/ak3/")
            }

            ShellUtils.runCommand("chmod -R 755 /data/local/tmp/pox_flasher/ak3")
            ShellUtils.runCommand("chown -R root:root /data/local/tmp/pox_flasher/ak3")

            // Verify update-binary
            val checkBin = ShellUtils.runCommand("test -f /data/local/tmp/pox_flasher/ak3/META-INF/com/google/android/update-binary")
            if (!checkBin.isSuccess) {
                send(FlashProgressEvent("Verification Error", "Error: META-INF/com/google/android/update-binary not found in zip archive!", isFinished = true, isSuccess = false))
                close()
                return@withContext
            }

            send(FlashProgressEvent("Executing AnyKernel3", "Starting AnyKernel3 installation..."))

            // 3. Execute update-binary with live streaming output
            val flashScript = """
                cd /data/local/tmp/pox_flasher/ak3
                export AKHOME=/data/local/tmp/pox_flasher/ak3
                export OUTFD=1
                export ZIPFILE='$stageZip'
                sh /data/local/tmp/pox_flasher/ak3/META-INF/com/google/android/update-binary 3 1 "${'$'}ZIPFILE" 2>&1
            """.trimIndent()

            val stdoutCallback = object : CallbackList<String>() {
                override fun onAddElement(s: String) {
                    val raw = s.trim()
                    if (raw.isEmpty()) return
                    val line = when {
                        raw.startsWith("ui_print ") -> raw.removePrefix("ui_print ").trim()
                        raw == "ui_print" -> ""
                        else -> raw
                    }
                    if (line.isNotEmpty()) {
                        trySend(FlashProgressEvent("Flashing", line))
                    }
                }
            }

            val execResult = Shell.cmd(flashScript).to(stdoutCallback, stdoutCallback).exec()

            // 4. Flush and Sync
            ShellUtils.runCommand("sync")

            if (execResult.isSuccess && execResult.code == 0) {
                send(FlashProgressEvent("Complete", "Kernel flashed successfully! Flash completed with exit code 0.", isFinished = true, isSuccess = true))
            } else {
                send(FlashProgressEvent("Failed", "Kernel flashing terminated with exit code ${execResult.code}", isFinished = true, isSuccess = false))
            }

            // Cleanup
            ShellUtils.runCommand("rm -rf /data/local/tmp/pox_flasher/ak3 /data/local/tmp/pox_flasher/package.zip")
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private fun unzipArchive(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val destFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    suspend fun flashViaOpenRecoveryScript(zipFile: File): Flow<String> = flow {
        emit("Checking root permissions...")
        val env = ShellUtils.environment()
        if (!env.granted) {
            throw Exception("Root access required to configure recovery flash.")
        }

        val backupPath = "/data/local/tmp/pox_boot_backup.img"
        emit("Backing up current boot partition to $backupPath...")
        val backup = ShellUtils.runCommand(
            "dd if=/dev/block/by-name/boot of=$backupPath bs=4M 2>/dev/null && sync"
        )
        if (!backup.isSuccess) {
            throw Exception("Boot backup failed - the partition was not written.")
        }

        val stagedZip = "/data/local/tmp/${zipFile.name}"
        emit("Staging $stagedZip...")
        val copy = ShellUtils.runCommand("cp '${zipFile.absolutePath}' '$stagedZip' && sync")
        if (!copy.isSuccess) {
            throw Exception("Could not stage the package - ${copy.output.lastOrNull()?.trim()}")
        }

        emit("Configuring OpenRecoveryScript for TWRP / OrangeFox...")
        ShellUtils.runCommand("mkdir -p /cache/recovery")
        val script = "/cache/recovery/openrecoveryscript"
        val stage = ShellUtils.runCommand(
            "printf '%s\\n' 'install $stagedZip' 'wipe cache' > $script"
        )
        if (!stage.isSuccess) {
            throw Exception("Could not stage $script - recovery directory may be unavailable.")
        }

        emit("Package staged successfully. Ready to reboot.")
    }.flowOn(Dispatchers.IO)

    suspend fun installAppUpdate(context: Context, downloadedFile: File): String = withContext(Dispatchers.IO) {
        var apkToInstall = downloadedFile

        // If it's a zip artifact from actions, extract the APK first
        if (downloadedFile.name.endsWith(".zip")) {
            val extractDir = File(context.cacheDir, "extracted_apk")
            extractDir.mkdirs()
            unzipArchive(downloadedFile, extractDir)
            val foundApk = extractDir.walkTopDown().firstOrNull { it.name.endsWith(".apk") }
            if (foundApk != null) {
                apkToInstall = foundApk
            }
        }

        // Try silent root install if root is granted
        val env = ShellUtils.environment()
        if (env.granted) {
            val res = ShellUtils.runCommand("pm install -r '${apkToInstall.absolutePath}'")
            if (res.isSuccess && res.output.any { it.contains("Success", true) }) {
                return@withContext "Installed successfully via root!"
            }
        }

        // Standard FileProvider intent install fallback
        withContext(Dispatchers.Main) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkToInstall
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
        "Installer launched. Please confirm the system update prompt."
    }

    suspend fun rebootToRecovery(): Boolean = withContext(Dispatchers.IO) {
        val res = ShellUtils.runCommand("/system/bin/reboot recovery || reboot recovery")
        res.isSuccess
    }

    suspend fun rebootSystem(): Boolean = withContext(Dispatchers.IO) {
        val res = ShellUtils.runCommand("/system/bin/reboot || reboot")
        res.isSuccess
    }
}

data class FlashProgressEvent(
    val step: String,
    val line: String,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

data class DownloadProgress(
    val percent: Int,
    val downloadedMb: Float,
    val totalMb: Float,
    val speedKbps: Float
)
