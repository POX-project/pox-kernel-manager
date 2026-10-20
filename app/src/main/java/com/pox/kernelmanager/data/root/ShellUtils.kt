package com.pox.kernelmanager.data.root

import android.os.SystemClock
import com.pox.kernelmanager.data.model.RootProvider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ShellResult(
    val isSuccess: Boolean,
    val output: List<String>,
    val exitCode: Int
)

/**
 * Everything the root layer learns from one batched shell probe.
 *
 * [provider] is which framework is installed and [granted] is whether *this* app
 * currently holds an active root shell. They are deliberately independent: a
 * device can be rooted while this app is denied, and reporting those as one
 * state is what made the old detector tell the user it was rooted when it was not.
 */
data class RootEnvironment(
    val provider: RootProvider,
    val version: String,
    val granted: Boolean,
    val zygisk: Boolean,
    val moduleCount: Int,
    val metamodule: String?,
    val probes: Map<String, String>
) {
    val isRooted: Boolean get() = granted
}

object ShellUtils {

    private const val PROBE_PREFIX = "pox."

    /**
     * One shell round-trip for the whole environment. Written flat and with each
     * test on its own line: `A || B && C` parses as `(A || B) && C` in POSIX sh,
     * which silently discarded half the output of the previous heuristic.
     *
     * The script always ends in `exit 0` so libsu reports success even when the
     * final `[ -e ... ]` test is false.
     */
    private val PROBE_SCRIPT: String = buildString {
        appendLine("echo \"${PROBE_PREFIX}uid=\$(id -u 2>/dev/null)\"")

        // Active su binary identification - highest authority on what is granting root
        appendLine("SU_V=\$(su -v 2>/dev/null)")
        appendLine("[ -n \"\$SU_V\" ] && echo \"${PROBE_PREFIX}su_v=\$SU_V\"")
        appendLine("SU_NUM=\$(su -V 2>/dev/null)")
        appendLine("[ -n \"\$SU_NUM\" ] && echo \"${PROBE_PREFIX}su_num=\$SU_NUM\"")

        // Magisk: running daemon and version check
        appendLine("MAGISK_V=\$(magisk -v 2>/dev/null)")
        appendLine("[ -n \"\$MAGISK_V\" ] && echo \"${PROBE_PREFIX}magisk_ver=\$MAGISK_V\"")
        appendLine("[ -e /dev/.magisk ] && echo \"${PROBE_PREFIX}magisk_dev=1\"")
        appendLine("[ -d /data/adb/magisk ] && echo \"${PROBE_PREFIX}magisk_dir=1\"")
        appendLine("[ -e /dev/.magisk-unblock ] && echo \"${PROBE_PREFIX}magisk_unblock=1\"")
        appendLine("pidof magiskd >/dev/null 2>&1 && echo \"${PROBE_PREFIX}magiskd_running=1\"")

        // KernelPatch / APatch
        appendLine("[ -e /dev/kp ] && echo \"${PROBE_PREFIX}kp=1\"")
        appendLine("[ -d /data/adb/ap ] && echo \"${PROBE_PREFIX}ap_dir=1\"")
        appendLine("[ -x /data/adb/ap/bin/apd ] && echo \"${PROBE_PREFIX}apd=1\"")
        appendLine("[ -x /data/adb/ap/bin/apstat ] && echo \"${PROBE_PREFIX}apstat=1\"")
        appendLine("[ -d /data/adb/ap/modules ] && echo \"${PROBE_PREFIX}ap_modules=1\"")
        appendLine("pidof apd >/dev/null 2>&1 && echo \"${PROBE_PREFIX}apd_running=1\"")
        appendLine("APD_V=\$(/data/adb/ap/bin/apd -V 2>/dev/null)")
        appendLine("[ -n \"\$APD_V\" ] && echo \"${PROBE_PREFIX}apd_ver=\$APD_V\"")

        // KernelSU lineage: REQUIRES active /sys/module/kernelsu driver
        appendLine("[ -e /sys/module/kernelsu ] && echo \"${PROBE_PREFIX}ksu_lkm=1\"")
        appendLine("[ -x /data/adb/ksud ] && echo \"${PROBE_PREFIX}ksud=1\"")
        appendLine("[ -x /data/adb/ksu/bin/ksud ] && echo \"${PROBE_PREFIX}ksud_bin=1\"")
        appendLine("[ -d /data/adb/ksu ] && echo \"${PROBE_PREFIX}ksu_dir=1\"")
        appendLine("[ -e /data/adb/ksu/.ksurc ] && echo \"${PROBE_PREFIX}ksurc=1\"")
        appendLine("[ -e /data/adb/ksu/lib/libadbroot.so ] && echo \"${PROBE_PREFIX}adbroot=1\"")
        appendLine("[ -e /data/adb/ksu/.dynamic_manager ] && echo \"${PROBE_PREFIX}dynamic_mgr=1\"")
        appendLine("[ -d /data/adb/ksu/module_configs ] && echo \"${PROBE_PREFIX}module_configs=1\"")
        appendLine("[ -e /data/adb/ksu/sulogd.lock ] && echo \"${PROBE_PREFIX}sulogd_lock=1\"")

        // Modules & Zygisk
        appendLine("grep -s -q 'zygisk' /data/adb/magisk/zygisk/* /data/adb/modules/*/module.prop 2>/dev/null && echo \"${PROBE_PREFIX}zygisk=1\"")
        appendLine("echo \"${PROBE_PREFIX}module_count=\$(ls -1 /data/adb/modules 2>/dev/null | wc -l | tr -d ' ')\"")
        appendLine("echo \"${PROBE_PREFIX}metamodule=\$(ls -1 /data/adb/metamodule 2>/dev/null | head -n 1)\"")

        // Legacy su binaries
        appendLine("for s in /system/bin/su /system/xbin/su /sbin/su /vendor/bin/su /debug_ramdisk/su /system/sbin/su /product/bin/su; do")
        appendLine("  [ -x \"\$s\" ] && echo \"${PROBE_PREFIX}su_bin=\$s\"")
        appendLine("done")

        // Installed managers
        appendLine("pm list packages 2>/dev/null | sed -n 's/^package://p' | " +
            "grep -E '^(package:)?(${RootProvider.MANAGER_PACKAGES.joinToString("|") { it.replace(".", "\\.") }})\$' | " +
            "sed 's/^package://' | sed 's/^/${PROBE_PREFIX}pkg=/'")

        appendLine("exit 0")
    }

    /** Root state is stable; re-probing is only worth it after a lifecycle change. */
    private const val GRANTED_TTL_MS = 30_000L

    /** A denial may be a prompt the user has not answered yet, so retry aggressively. */
    private const val DENIED_TTL_MS = 2_000L

    private val probeMutex = Mutex()

    private class Cache(val environment: RootEnvironment, val createdAt: Long)

    @Volatile
    private var cached: Cache? = null

    init {
        Shell.enableVerboseLogging = false
        runCatching {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(15)
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
            )
        }
    }

    /** Obtain a root shell ahead of the first UI read so later calls are instant. */
    suspend fun prewarm() = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell() }
        Unit
    }

    /** Drop cached probe results. Call when returning to the foreground. */
    fun invalidateCache() {
        cached = null
    }

    suspend fun environment(): RootEnvironment = withContext(Dispatchers.IO) {
        probeMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            cached?.let { entry ->
                val ttl = if (entry.environment.granted) GRANTED_TTL_MS else DENIED_TTL_MS
                if (now - entry.createdAt < ttl) return@withLock entry.environment
            }
            val fresh = probe()
            cached = Cache(fresh, now)
            fresh
        }
    }

    private suspend fun probe(): RootEnvironment {
        val shell = runCatching { Shell.getShell() }.getOrNull()
            ?: return RootEnvironment(RootProvider.ROOTLESS, "", false, false, 0, null, emptyMap())
        val granted = shell.isRoot

        val output = runCatching { Shell.cmd(PROBE_SCRIPT).exec() }.getOrNull()?.out.orEmpty()
        val probes = parseProbe(output)

        val packages = probes.entries
            .filter { it.key == "pkg" }
            .map { it.value }
            .toSet()

        val provider = classifyRoot(probes, packages)

        return RootEnvironment(
            provider = provider,
            version = fetchVersion(provider, probes),
            granted = granted,
            zygisk = "zygisk" in probes,
            moduleCount = probes["module_count"]?.toIntOrNull() ?: 0,
            metamodule = probes["metamodule"]?.takeIf { it.isNotBlank() },
            probes = probes
        )
    }

    private fun parseProbe(output: List<String>): Map<String, String> {
        val map = HashMap<String, String>()
        for (line in output) {
            val trimmed = line.trim()
            if (!trimmed.startsWith(PROBE_PREFIX)) continue
            val body = trimmed.substring(PROBE_PREFIX.length)
            val sep = body.indexOf('=')
            if (sep <= 0) continue
            map[body.substring(0, sep)] = body.substring(sep + 1).trim()
        }
        return map
    }

    private fun classifyRoot(probes: Map<String, String>, packages: Set<String>): RootProvider {
        val suV = probes["su_v"]?.lowercase().orEmpty()

        // 1. First priority: active su binary response. The binary handling the root shell is definitive.
        if (suV.contains("apatch") || suV.contains("kernelpatch") || suV.contains("folkpatch") || suV.contains("folk")) {
            return if (suV.contains("kernelpatch")) RootProvider.KERNELPATCH else RootProvider.APATCH
        }
        if (suV.contains("kernelsu") || suV.contains("ksu")) {
            val bakasu = "adbroot" in probes || "dynamic_mgr" in probes ||
                "com.resukisu.resukisu" in packages || "org.bakasu.bakasu" in packages
            val ksuNext = "com.rifsxd.ksunext" in packages
            val sukisu = "com.sukisu.ultra" in packages
            return when {
                bakasu -> RootProvider.BAKASU
                ksuNext -> RootProvider.KERNELSU_NEXT
                sukisu -> RootProvider.SUKISU_ULTRA
                else -> RootProvider.KERNELSU
            }
        }
        if (suV.contains("magisk")) {
            return RootProvider.MAGISK
        }
        if (suV.contains("supersu")) {
            return RootProvider.SUPERSU
        }

        // 2. Running active daemons and kernel interfaces
        val hasApd = "apd_running" in probes || "apd" in probes || "ap_dir" in probes || "kp" in probes
        val hasKsu = "ksu_lkm" in probes || "ksud_running" in probes || "ksud_bin" in probes
        val hasMagisk = "magiskd_running" in probes && "magisk_ver" in probes

        // Active APatch daemon takes precedence over dormant Magisk leftovers
        if (hasApd) {
            val isKpOnly = "kp" in probes && !hasApd && "me.bmax.kernelpatch" in packages
            return if (isKpOnly) RootProvider.KERNELPATCH else RootProvider.APATCH
        }

        if (hasKsu) {
            val bakasu = "adbroot" in probes || "dynamic_mgr" in probes ||
                "com.resukisu.resukisu" in packages || "org.bakasu.bakasu" in packages
            val ksuNext = "com.rifsxd.ksunext" in packages
            val sukisu = "com.sukisu.ultra" in packages
            return when {
                bakasu -> RootProvider.BAKASU
                ksuNext -> RootProvider.KERNELSU_NEXT
                sukisu -> RootProvider.SUKISU_ULTRA
                else -> RootProvider.KERNELSU
            }
        }

        if (hasMagisk || ("magiskd_running" in probes && "com.topjohnwu.magisk" in packages && !hasApd && !hasKsu)) {
            return RootProvider.MAGISK
        }

        // 3. Fallback to manager packages
        val byPkg = classifyByPackage(packages)
        if (byPkg != null) return byPkg

        // 4. Fallback to markers
        return classifyByMarkers(probes) ?: RootProvider.UNKNOWN
    }

    private fun classifyByPackage(packages: Set<String>): RootProvider? {
        if (packages.isEmpty()) return null
        for (provider in RootProvider.values()) {
            if (provider.managerPackages.any { it in packages }) return provider
        }
        return null
    }

    private fun classifyByMarkers(probes: Map<String, String>): RootProvider? = when {
        "kp" in probes -> {
            if ("apd" in probes || "ap_dir" in probes) RootProvider.APATCH else RootProvider.KERNELPATCH
        }
        "ksu_lkm" in probes -> {
            val bakasu = "adbroot" in probes || "dynamic_mgr" in probes ||
                "module_configs" in probes || "sulogd_lock" in probes
            if (bakasu) RootProvider.BAKASU else RootProvider.KERNELSU
        }
        "magisk_dev" in probes || "magisk_dir" in probes || "magisk_ver" in probes -> RootProvider.MAGISK
        "su_bin" in probes -> RootProvider.SUPERSU
        else -> null
    }

    private suspend fun fetchVersion(provider: RootProvider, probes: Map<String, String>): String {
        if ("uid" !in probes) return ""
        probes["su_v"]?.takeIf { it.isNotBlank() }?.let { return it }
        probes["su_num"]?.takeIf { it.isNotBlank() }?.let { return it }
        if (provider == RootProvider.MAGISK && probes["magisk_ver"] != null) {
            return probes["magisk_ver"]!!
        }
        if (provider == RootProvider.APATCH && probes["apd_ver"] != null) {
            return probes["apd_ver"]!!
        }
        val candidates = when (provider) {
            RootProvider.BAKASU, RootProvider.KERNELSU_NEXT,
            RootProvider.SUKISU_ULTRA, RootProvider.KERNELSU ->
                listOf("/data/adb/ksu/bin/ksud -V", "/data/adb/ksud -V")
            RootProvider.APATCH, RootProvider.KERNELPATCH -> listOf("/data/adb/ap/bin/apd -V", "/data/adb/apd -V")
            RootProvider.MAGISK -> listOf("magisk -v", "magisk -V")
            else -> emptyList()
        }
        for (command in candidates) {
            val text = runCatching { Shell.cmd(command).exec() }.getOrNull()
            val line = text?.out?.firstOrNull { it.isNotBlank() }?.trim()
            if (!line.isNullOrEmpty()) return line
        }
        return ""
    }

    /**
     * App-side su probe. Kept for callers that need a cheap pre-check, but it is
     * only a hint: `File.exists()` runs in the app mount namespace, where the
     * `/data/adb` tree is often invisible and a magic-mounted `su` may have no
     * backing file on disk at all.
     */
    fun hasSuBinary(): Boolean = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su",
        "/debug_ramdisk/su", "/system/sbin/su", "/product/bin/su",
        "/data/adb/ksud", "/data/adb/ksu/bin/ksud", "/data/adb/ap/bin/apd"
    ).any { runCatching { File(it).exists() }.getOrDefault(false) }

    suspend fun isRootAvailable(): Boolean = environment().granted

    suspend fun detectRootProvider(): RootProvider = environment().provider

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
        val quoted = shellQuote(path)

        // Only attempt direct File read for world-readable paths that won't trigger SELinux denial audits
        try {
            if (!path.startsWith("/sys/kernel/") && !path.startsWith("/proc/sys/kernel/")) {
                val file = File(path)
                if (file.canRead()) {
                    val content = file.bufferedReader().use { it.readText() }.trim()
                    if (content.isNotEmpty()) return@withContext content
                }
            }
        } catch (_: Exception) { }

        if (!isRootAvailable()) return@withContext null
        try {
            val res = Shell.cmd("cat $quoted 2>/dev/null").exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                return@withContext res.out.joinToString("\n").trim()
            }
        } catch (_: Exception) { }
        null
    }

/**
 * Write a value and confirm it landed.
 *
 * Every tuning setter used to discard this return value and report success
 * regardless, so a missing `/proc/perfmgr` node looked identical to a working
 * write. The read-back below is what makes the reported result trustworthy.
 *
 * [verify] receives the node contents after the write. The default is correct for
 * sysfs files, where writing and reading back the same value round-trips. Nodes
 * with a command interface (MediaTek's `usrtch`) echo back a status dump instead,
 * so they must supply their own predicate.
 */
suspend fun fastWrite(
    path: String,
    value: String,
    verify: ((String) -> Boolean)? = null
): Boolean = withContext(Dispatchers.IO) {
    val quotedPath = shellQuote(path)

    try {
        val file = File(path)
        if (file.exists() && file.canWrite()) {
            file.writeText(value)
            if (verify == null) return@withContext true
            val content = file.readText().trim()
            return@withContext verify(content)
        }
    } catch (_: Exception) { }

    if (!isRootAvailable()) return@withContext false

    try {
        val written = Shell.cmd("printf '%s\\n' ${shellQuote(value)} > $quotedPath").exec()
        if (!written.isSuccess) return@withContext false

        if (verify != null) {
            val readBack = Shell.cmd("cat $quotedPath 2>/dev/null").exec()
            val content = readBack.out.joinToString("\n")
            return@withContext verify(content)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Batch-test write access for a collection of node paths in a single shell execution.
 * Eliminates running dozens of separate subshells during hardware probing.
 */
suspend fun batchCheckWritable(paths: List<String>): Set<String> = withContext(Dispatchers.IO) {
    if (paths.isEmpty() || !isRootAvailable()) return@withContext emptySet()
    val script = buildString {
        for (p in paths) {
            append("[ -w ").append(shellQuote(p)).append(" ] && echo ").append(shellQuote(p)).append("\n")
        }
    }
    val res = runCatching { Shell.cmd(script).exec() }.getOrNull()
    res?.out?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}

/**
 * Batch-read multiple node paths in a single shell execution to minimize IPC latency.
 */
suspend fun batchRead(paths: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
    if (paths.isEmpty() || !isRootAvailable()) return@withContext emptyMap()
    val script = buildString {
        for (p in paths) {
            append("echo \"__POX_NODE__:$p\"\n")
            append("cat ${shellQuote(p)} 2>/dev/null\n")
        }
    }
    val res = runCatching { Shell.cmd(script).exec() }.getOrNull() ?: return@withContext emptyMap()
    val map = HashMap<String, String>()
    var curPath: String? = null
    val curLines = mutableListOf<String>()

    for (line in res.out) {
        if (line.startsWith("__POX_NODE__:")) {
            if (curPath != null) {
                map[curPath] = curLines.joinToString("\n").trim()
                curLines.clear()
            }
            curPath = line.substring("__POX_NODE__:".length)
        } else {
            curLines.add(line)
        }
    }
    if (curPath != null) {
        map[curPath] = curLines.joinToString("\n").trim()
    }
    map
}

/**
 * Read a node through the root shell, bypassing the app mount namespace.
 *
 * `/proc/perfmgr` and `/data/adb` are frequently invisible to plain `File` reads
 * on a device with a root framework installed, so procfs and the kernel-supplied
 * tuning nodes must always be read through the shell.
 */
suspend fun readNode(path: String): String? = withContext(Dispatchers.IO) {
    if (!isRootAvailable()) return@withContext null
    try {
        val res = Shell.cmd("cat ${shellQuote(path)} 2>/dev/null").exec()
        val text = res.out.joinToString("\n").trim()
        if (text.isEmpty()) null else text
    } catch (_: Exception) {
        null
    }
}

/** One probed kernel node and what it returned. */
data class NodeStatus(
    val path: String,
    val exists: Boolean,
    val writable: Boolean,
    val sample: String?
)

/**
 * Every node this build looks for, with what it actually returned.
 *
 * Exposed so the diagnostics screen can show the real state of the device
 * instead of a guess, and so someone reporting "everything is greyed out" can
 * share the raw output.
 */
suspend fun nodeReport(paths: List<String>): List<NodeStatus> =
    withContext(Dispatchers.IO) {
        val granted = isRootAvailable()
        paths.map { path ->
            if (!granted) {
                NodeStatus(path, exists = false, writable = false, sample = null)
            } else {
                val exists = runCatching {
                    Shell.cmd("test -e ${shellQuote(path)}").exec().isSuccess
                }.getOrDefault(false)
                val writable = runCatching {
                    Shell.cmd("test -w ${shellQuote(path)}").exec().isSuccess
                }.getOrDefault(false)
                val sample = runCatching {
                    Shell.cmd("cat ${shellQuote(path)} 2>/dev/null | head -c 120").exec()
                        .out.joinToString(" ").trim()
                }.getOrNull()
                NodeStatus(path, exists, writable, sample?.takeIf { it.isNotEmpty() })
            }
        }
    }

fun rootKnownGranted(): Boolean = cached?.environment?.granted == true

suspend fun nodeExists(path: String): Boolean = withContext(Dispatchers.IO) {
    if (!isRootAvailable()) return@withContext false
    try {
        Shell.cmd("test -w ${shellQuote(path)}").exec().isSuccess
    } catch (_: Exception) {
        false
    }
}

    /** Single-quote a value for safe interpolation into a shell command. */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
 * Whether the kernel is unmounting modules on demand.
 *
 * `/dev/nomount` never existed. KernelSU and its forks implement this as feature
 * id 1 (`KernelUmount`) inside the kernel's feature-handler interface, queried
 * through the ksud CLI, which prints `Value: <0|1>` and `Status: enabled|disabled`.
 */
suspend fun detectNoMount(): Boolean = withContext(Dispatchers.IO) {
    if (!isRootAvailable()) return@withContext false

    val result = runCatching {
        Shell.cmd("/data/adb/ksu/bin/ksud feature get kernel_umount 2>/dev/null").exec()
    }.getOrNull() ?: return@withContext false

    if (!result.isSuccess) return@withContext false

    val text = result.out.joinToString("\n")
    // "Status: enabled" is authoritative; fall back to the raw value.
    if (text.contains("enabled", ignoreCase = true) && !text.contains("disabled", ignoreCase = true)) {
        return@withContext true
    }
    Regex("(?m)^Value:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() == 1
}

    suspend fun detectSELinux(): String = withContext(Dispatchers.IO) {
        try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists() && file.canRead()) {
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