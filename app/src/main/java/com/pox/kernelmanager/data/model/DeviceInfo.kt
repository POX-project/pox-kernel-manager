package com.pox.kernelmanager.data.model

data class DeviceInfo(
    val deviceModel: String = "Redmi Note 8 Pro",
    val codename: String = "begonia",
    val socName: String = "MediaTek MT6785 (Helio G90T)",
    val kernelRelease: String = "",
    val linuxVersion: String = "",
    val kernelEdition: String = "",
    val commitHash: String = "",
    val isPoxKernel: Boolean = false,
    val rootProvider: RootProvider = RootProvider.UNKNOWN,
    val rootProviderVersion: String = "",
    val rootGranted: Boolean = false,
    val selinuxStatus: String = "Enforcing",
    val noMountActive: Boolean = false
) {
    val isM20: Boolean get() = isM20Device(codename)
    val maxHardwareWatt: Int get() = if (isM20) 15 else 18
    val nominalBatteryCapacityMah: Int get() = if (isM20) 5000 else 4500
    val maxBigOppKhz: Int get() = if (isM20) 1768000 else 2050000
    val primaryKernelRepo: String get() = if (isM20) "txorav/pox-kernel-m20lte" else "POX-project/pox-begonia-kernel"
    val fallbackKernelRepo: String get() = if (isM20) "txorav/pox-kernel-m20lte" else "txorav/pox-kernel-begonia"

    companion object {
        fun isM20Device(
            device: String = "",
            product: String = "",
            procVersion: String = ""
        ): Boolean {
            val d = device.lowercase()
            val p = product.lowercase()
            val v = procVersion.lowercase()
            return d.contains("m20") || p.contains("m20") ||
                   v.contains("universal7885") || v.contains("exynos7885") || v.contains("m20lte")
        }
    }
}

/**
 * Root frameworks POX ships against, plus the common community alternatives.
 *
 * [managerPackages] are the `applicationId`s of each project's official manager APK.
 * They are matched against `pm list packages` and are the only reliable discriminator
 * between KernelSU forks: every fork shares the same on-disk layout (`/data/adb/ksu`,
 * `/data/adb/ksud`) and the same `su` interface, so path probing alone cannot tell
 * BakaSU from SukiSU from upstream KernelSU.
 *
 * Declaration order is detection priority. Keep the more specific forks above the
 * generic ones they would otherwise be swallowed by.
 */
enum class RootProvider(val displayName: String, val managerPackages: List<String>) {

    APATCH("APatch", listOf("me.bmax.apatch", "me.yuki.folk")),

    KERNELPATCH("KernelPatch", listOf("me.bmax.kernelpatch")),

    // BakaSU ships applicationId `com.resukisu.resukisu` (its former name, ReSukiSU);
    // `org.bakasu.bakasu` is only the Gradle namespace and never appears in the APK id.
    BAKASU("BakaSU", listOf("com.resukisu.resukisu", "org.bakasu.bakasu")),

    KERNELSU_NEXT("KernelSU-Next", listOf("com.rifsxd.ksunext")),

    SUKISU_ULTRA("SukiSU-Ultra", listOf("com.sukisu.ultra")),

    KERNELSU("KernelSU", listOf("me.weishu.kernelsu")),

    MAGISK("Magisk", listOf("com.topjohnwu.magisk")),

    SUPERSU("SuperSU", listOf("eu.chainfire.supersu", "com.noshufou.android.su", "com.thirdparty.superuser")),

    ROOTLESS("Unrooted", emptyList()),

    UNKNOWN("Unknown", emptyList());

    /** True only for frameworks we could positively identify. */
    val isRooted: Boolean get() = this != ROOTLESS && this != UNKNOWN

    /** True for the KernelSU lineage (KernelSU, its forks, and APatch's UI heritage). */
    val isKernelSuFamily: Boolean get() = this in KERNEL_SU_FAMILY

    companion object {

        val KERNEL_SU_FAMILY: Set<RootProvider> = setOf(
            BAKASU, KERNELSU_NEXT, SUKISU_ULTRA, KERNELSU
        )

        /** Every manager package id we know how to identify, for the batched `pm` probe. */
        val MANAGER_PACKAGES: List<String> =
            values().filter { it.managerPackages.isNotEmpty() }.flatMap { it.managerPackages }.distinct()

        private val MANAGER_PACKAGE_REGEX: Regex = Regex(
            "^(?:package:)?(" + MANAGER_PACKAGES.joinToString("|") { Regex.escape(it) } + ")$"
        )

        fun matchesManagerPackageName(line: String): Boolean = MANAGER_PACKAGE_REGEX.matches(line.trim())
    }
}