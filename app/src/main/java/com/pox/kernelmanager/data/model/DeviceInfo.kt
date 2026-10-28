package com.pox.kernelmanager.data.model

data class DeviceInfo(
    val deviceModel: String = "Samsung Galaxy M20",
    val codename: String = "m20lte",
    val socName: String = "Samsung Exynos 7904 (universal7885)",
    val kernelRelease: String = "",
    val linuxVersion: String = "Linux 4.4",
    val kernelEdition: String = "",
    val commitHash: String = "",
    val isPoxKernel: Boolean = false,
    val rootProvider: RootProvider = RootProvider.UNKNOWN,
    val rootProviderVersion: String = "",
    val rootGranted: Boolean = false,
    val selinuxStatus: String = "Enforcing",
    val noMountActive: Boolean = false
) {
    val maxHardwareWatt: Int get() = 15
    val nominalBatteryCapacityMah: Int get() = 5000
    val maxBigOppKhz: Int get() = 1768000
    val primaryKernelRepo: String get() = "txorav/pox-kernel-m20lte"
    val fallbackKernelRepo: String get() = "txorav/pox-kernel-m20lte"

    val cpuArchDesc: String get() = "ARM64 v8-A (2x A73 + 6x A53)"
    val bigClusterName: String get() = "Cortex-A73"
    val littleClusterName: String get() = "Cortex-A53"
    val gpuModelName: String get() = "ARM Mali-G71 MP2"
    val storageTypeName: String get() = "eMMC 5.1 Flash"
    val displayTypeName: String get() = "PLS TFT (Full HD+)"
    val fastChargeDesc: String get() = "Samsung Adaptive Fast Charging (15W AFC)"
}

/**
 * Root frameworks POX ships against, plus the common community alternatives.
 */
enum class RootProvider(val displayName: String, val managerPackages: List<String>) {

    APATCH("APatch", listOf("me.bmax.apatch", "me.yuki.folk")),

    KERNELPATCH("KernelPatch", listOf("me.bmax.kernelpatch")),

    BAKASU("BakaSU", listOf("com.resukisu.resukisu", "org.bakasu.bakasu")),

    KERNELSU_NEXT("KernelSU-Next", listOf("com.rifsxd.ksunext")),

    SUKISU_ULTRA("SukiSU-Ultra", listOf("com.sukisu.ultra")),

    KERNELSU("KernelSU", listOf("me.weishu.kernelsu")),

    MAGISK("Magisk", listOf("com.topjohnwu.magisk")),

    SUPERSU("SuperSU", listOf("eu.chainfire.supersu", "com.noshufou.android.su", "com.thirdparty.superuser")),

    ROOTLESS("Unrooted", emptyList()),

    UNKNOWN("Unknown", emptyList());

    val isRooted: Boolean get() = this != ROOTLESS && this != UNKNOWN

    val isKernelSuFamily: Boolean get() = this in KERNEL_SU_FAMILY

    companion object {

        val KERNEL_SU_FAMILY: Set<RootProvider> = setOf(
            BAKASU, KERNELSU_NEXT, SUKISU_ULTRA, KERNELSU
        )

        val MANAGER_PACKAGES: List<String> =
            values().filter { it.managerPackages.isNotEmpty() }.flatMap { it.managerPackages }.distinct()

        private val MANAGER_PACKAGE_REGEX: Regex = Regex(
            "^(?:package:)?(" + MANAGER_PACKAGES.joinToString("|") { Regex.escape(it) } + ")$"
        )

        fun matchesManagerPackageName(line: String): Boolean = MANAGER_PACKAGE_REGEX.matches(line.trim())
    }
}
