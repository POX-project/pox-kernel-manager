package com.pox.kernelmanager.data.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified high-performance communication bridge between Pox Manager and Pox Kernel.
 * Uses /proc/perfmgr/pox_config and /sys/kernel/pox_config as a single-file atomic interface.
 *
 * Eliminates dozens of shell forks and context switches that previously caused ADB buffer
 * flooding, high PC CPU usage, and device stutter.
 */
object PoxConfigBridge {
    const val POX_CONFIG_PROC = "/proc/perfmgr/pox_config"
    const val POX_CONFIG_SYS = "/sys/kernel/pox_config"
    const val POX_CONFIG_PERSIST = "/data/adb/pox/pox_config.conf"

    private val cachedConfig: MutableMap<String, String> = HashMap()
    private var lastReadTime: Long = 0L

    /**
     * Read the entire kernel configuration in a single atomic operation.
     * Attempts fast direct file read first (takes ~0.05ms with 0% CPU and no root shell),
     * then falls back to root shell read if SELinux blocks untrusted app file read.
     */
    suspend fun readConfig(forceRefresh: Boolean = false): Map<String, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && (now - lastReadTime) < 1500L && cachedConfig.isNotEmpty()) {
            return@withContext cachedConfig
        }

        // 1. Try direct userspace read (node created with mode 0666 in kernel)
        var rawText: String? = null
        try {
            val procFile = File(POX_CONFIG_PROC)
            if (procFile.exists() && procFile.canRead()) {
                rawText = procFile.readText()
            }
        } catch (_: Exception) { }

        // 2. Try sysfs alias
        if (rawText.isNullOrBlank()) {
            try {
                val sysFile = File(POX_CONFIG_SYS)
                if (sysFile.exists() && sysFile.canRead()) {
                    rawText = sysFile.readText()
                }
            } catch (_: Exception) { }
        }

        // 3. Fallback to fast shell read if direct read was blocked by SELinux
        if (rawText.isNullOrBlank() && ShellUtils.isRootAvailable()) {
            rawText = ShellUtils.fastRead(POX_CONFIG_PROC) ?: ShellUtils.fastRead(POX_CONFIG_SYS)
        }

        if (!rawText.isNullOrBlank()) {
            val map = parseConfig(rawText)
            cachedConfig.clear()
            cachedConfig.putAll(map)
            lastReadTime = now
            return@withContext map
        }

        cachedConfig
    }

    /**
     * Parse key=value or key: value format lines
     */
    fun parseConfig(content: String): Map<String, String> {
        val map = HashMap<String, String>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val sepIdx = trimmed.indexOfFirst { it == '=' || it == ':' }
            if (sepIdx > 0) {
                val key = trimmed.substring(0, sepIdx).trim()
                val value = trimmed.substring(sepIdx + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    /**
     * Write a single configuration key=value to the unified kernel config node.
     */
    suspend fun writeConfig(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        val payload = "$key=$value\n"
        cachedConfig[key] = value
        writeRaw(payload)
    }

    /**
     * Write multiple configuration keys atomically in a single write call.
     * Eliminates dozens of subshell invocations when applying profiles or batches.
     */
    suspend fun writeBatch(entries: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext true
        val payload = buildString {
            for ((k, v) in entries) {
                append(k).append('=').append(v).append('\n')
            }
        }
        cachedConfig.putAll(entries)
        writeRaw(payload)
    }

    private suspend fun writeRaw(payload: String): Boolean {
        // 1. Try direct userspace write (mode 0666)
        try {
            val procFile = File(POX_CONFIG_PROC)
            if (procFile.exists() && procFile.canWrite()) {
                procFile.writeText(payload)
                return true
            }
        } catch (_: Exception) { }

        // 2. Try sysfs alias
        try {
            val sysFile = File(POX_CONFIG_SYS)
            if (sysFile.exists() && sysFile.canWrite()) {
                sysFile.writeText(payload)
                return true
            }
        } catch (_: Exception) { }

        // 3. Fallback to ShellUtils fastWrite
        if (ShellUtils.isRootAvailable()) {
            val res = ShellUtils.fastWrite(POX_CONFIG_PROC, payload)
            if (res) return true
            return ShellUtils.fastWrite(POX_CONFIG_SYS, payload)
        }

        return false
    }

    /**
     * Checks if the unified configuration node is supported on the active kernel.
     */
    suspend fun isSupported(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (File(POX_CONFIG_PROC).exists() || File(POX_CONFIG_SYS).exists()) return@withContext true
        } catch (_: Exception) { }
        ShellUtils.fastRead(POX_CONFIG_PROC) != null || ShellUtils.fastRead(POX_CONFIG_SYS) != null
    }

    /**
     * Invalidate internal cache on demand
     */
    fun invalidateCache() {
        cachedConfig.clear()
        lastReadTime = 0L
    }
}
