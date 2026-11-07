package com.goydevv.arseide

object XedConstants {
    /** Canonical GitHub repository for Arse IDE (used for releases, changelog, updates, and issue reports). */
    const val GITHUB_OWNER_REPO = "GoyDevv/ArseIDE"
    const val GITHUB_REPO = "https://github.com/GoyDevv/ArseIDE"
    const val GITHUB_RELEASES_URL = "https://github.com/GoyDevv/ArseIDE/releases"
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/GoyDevv/ArseIDE/releases"

    /**
     * Primary and fallback Ubuntu 24.04 rootfs mirrors.
     * Fixes the 404 error caused by the broken space in "Arse IDE/Karbon-PackagesX" and adds
     * official Ubuntu cdimage fallback URLs so rootfs downloads never 404.
     */
    private const val ROOTFS_BASE = "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"
    private const val UBUNTU_CDIMAGE_BASE = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release"

    const val ROOTFS_ARM = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-armhf.tar.gz"
    const val ROOTFS_ARM64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-arm64.tar.gz"
    const val ROOTFS_X64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-amd64.tar.gz"

    fun rootfsUrlsForAbis(abis: Array<String>): List<String> {
        val arch =
            when {
                abis.contains("x86_64") -> "amd64"
                abis.contains("arm64-v8a") -> "arm64"
                else -> "armhf"
            }
        return listOf(
            "$ROOTFS_BASE/ubuntu-base-24.04.3-base-$arch.tar.gz",
            "$UBUNTU_CDIMAGE_BASE/ubuntu-base-24.04.3-base-$arch.tar.gz",
            "$UBUNTU_CDIMAGE_BASE/ubuntu-base-24.04.2-base-$arch.tar.gz",
            "$UBUNTU_CDIMAGE_BASE/ubuntu-base-24.04.1-base-$arch.tar.gz",
        )
    }

    const val EXTENSION_API_BASE = "https://xed-editor.app/api/extensions"
    const val THEMES_API_BASE = "https://xed-editor.app/api/themes"
    const val ICONPACKS_API_BASE = "https://xed-editor.app/api/icon-packs"
}
