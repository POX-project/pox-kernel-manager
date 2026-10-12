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
    val selinuxStatus: String = "Enforcing",
    val noMountActive: Boolean = false
)

enum class RootProvider(val displayName: String) {
    APATCH("APatch"),
    KERNELSU_NEXT("KernelSU-Next"),
    KERNELSU("KernelSU"),
    MAGISK("Magisk"),
    ROOTLESS("Unrooted"),
    UNKNOWN("Checking...")
}
