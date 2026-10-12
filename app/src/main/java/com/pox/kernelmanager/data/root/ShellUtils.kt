package com.pox.kernelmanager.data.root

import com.topjohnwu.superuser.Shell
import com.pox.kernelmanager.data.model.RootProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ShellResult(
    val isSuccess: Boolean,
    val output: List<String>,
    val exitCode: Int
)

object ShellUtils {

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    init {
        try {
            Shell.enableVerboseLogging = false
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(8)
            )
        } catch (_: Throwable) {
            // Ignore if shell is already initialized
        }
    }

    fun hasSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/vendor/bin/su", "/data/adb/ksu/bin/ksud", "/data/adb/ap/bin/apstat"
        )
        return paths.any {
            try {
                File(it).exists()
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        cachedRootAvailable?.let { return@withContext it }
        val available = try {
            if (Shell.isAppGrantedRoot() == true) {
                true
            } else if (hasSuBinary()) {
                Shell.getShell().isRoot
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
        cachedRootAvailable = available
        available
    }

    fun clearRootCache() {
        cachedRootAvailable = null
    }

    suspend fun runCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext ShellResult(
                isSuccess = false,
                output = listOf("Root access is not available"),
                exitCode = -1
            )
        }
        try {
            val result = Shell.cmd(command).exec()
            ShellResult(
                isSuccess = result.isSuccess,
                output = result.out,
                exitCode = result.code
            )
        } catch (e: Exception) {
            ShellResult(
                isSuccess = false,
                output = listOf(e.message ?: "Execution failed"),
                exitCode = -1
            )
        }
    }

    suspend fun fastRead(path: String): String? = withContext(Dispatchers.IO) {
        // Direct read attempt first (works without root for world-readable /sys and /proc)
        try {
            val file = File(path)
            if (file.exists()) {
                val content = file.bufferedReader().use { it.readText() }.trim()
                if (content.isNotEmpty()) return@withContext content
            }
        } catch (_: Exception) { }

        // Fall back to root read ONLY if root is available
        if (isRootAvailable()) {
            try {
                val res = Shell.cmd("cat \"$path\" 2>/dev/null").exec()
                if (res.isSuccess && res.out.isNotEmpty()) {
                    return@withContext res.out.joinToString("\n").trim()
                }
            } catch (_: Exception) { }
        }
        null
    }

    suspend fun fastWrite(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        // Direct write attempt first
        try {
            val file = File(path)
            if (file.exists()) {
                file.writeText(value)
                return@withContext true
            }
        } catch (_: Exception) { }

        // Fall back to root write if root is available
        if (isRootAvailable()) {
            try {
                val res = Shell.cmd("echo \"$value\" > \"$path\"").exec()
                return@withContext res.isSuccess
            } catch (_: Exception) { }
        }
        false
    }

    suspend fun detectRootProvider(): RootProvider = withContext(Dispatchers.IO) {
        try {
            if (!isRootAvailable()) return@withContext RootProvider.ROOTLESS

            // 1. APatch detection
            val apatchCheck = Shell.cmd("which apstat 2>/dev/null || [ -e /dev/kp ] && echo apatch").exec()
            if (apatchCheck.out.any { it.contains("apatch") || it.contains("apstat") }) {
                return@withContext RootProvider.APATCH
            }

            // 2. KernelSU / KernelSU-Next detection
            val ksuCheck = Shell.cmd("[ -e /data/adb/ksu ] && cat /data/adb/ksu/bin/ksud -V 2>/dev/null || echo \"\"").exec()
            val ksuVersion = ksuCheck.out.joinToString("")
            if (ksuVersion.contains("Next", ignoreCase = true) || File("/sys/module/kernelsu").exists()) {
                return@withContext RootProvider.KERNELSU_NEXT
            } else if (File("/data/adb/ksu").exists()) {
                return@withContext RootProvider.KERNELSU
            }

            // 3. Magisk detection
            val magiskCheck = Shell.cmd("magisk -v 2>/dev/null || which magisk").exec()
            if (magiskCheck.isSuccess && magiskCheck.out.isNotEmpty()) {
                return@withContext RootProvider.MAGISK
            }

            return@withContext RootProvider.UNKNOWN
        } catch (_: Exception) {
            RootProvider.ROOTLESS
        }
    }

    suspend fun detectNoMount(): Boolean = withContext(Dispatchers.IO) {
        try {
            val directFile = File("/dev/nomount")
            if (directFile.exists()) return@withContext true
        } catch (_: Exception) { }

        if (isRootAvailable()) {
            try {
                val check = Shell.cmd("[ -e /dev/nomount ] || grep -q \"nomount\" /proc/keys 2>/dev/null && echo active").exec()
                return@withContext check.out.any { it.contains("active") }
            } catch (_: Exception) { }
        }
        false
    }

    suspend fun detectSELinux(): String = withContext(Dispatchers.IO) {
        // Try reading /sys/fs/selinux/enforce directly
        try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists()) {
                val content = file.bufferedReader().use { it.readText() }.trim()
                return@withContext if (content == "0") "Permissive" else "Enforcing"
            }
        } catch (_: Exception) { }

        if (isRootAvailable()) {
            try {
                val res = Shell.cmd("getenforce 2>/dev/null").exec()
                val text = res.out.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) return@withContext text
            } catch (_: Exception) { }
        }
        "Enforcing"
    }
}
