package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import kotlin.test.*

class ReleaseInfoTest {

    @Test
    fun testCleanVersionStripsPrefixes() {
        assertEquals("1.0.0", ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com").cleanVersion)
        assertEquals("2.3.4-beta.1", ReleaseInfo(tagName = "V2.3.4-beta.1", htmlUrl = "https://github.com").cleanVersion)
        assertEquals("3.0.0", ReleaseInfo(tagName = "3.0.0", htmlUrl = "https://github.com").cleanVersion)
        assertEquals("1.2.3", ReleaseInfo(tagName = "  v1.2.3  ", htmlUrl = "https://github.com").cleanVersion)
    }

    @Test
    fun testResolvePlatformAssetMacOSArm64() {
        val assets = listOf(
            ReleaseAsset(name = "antigravity-studio-1.0.0-macos-x64.dmg", downloadUrl = "https://github.com/dmg1"),
            ReleaseAsset(name = "antigravity-studio-1.0.0-macos-arm64.dmg", downloadUrl = "https://github.com/dmg2"),
            ReleaseAsset(name = "antigravity-studio-1.0.0-windows-x64.exe", downloadUrl = "https://github.com/exe1")
        )
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com", assets = assets)

        val asset = release.resolvePlatformAsset(osName = "Mac OS X", osArch = "aarch64")
        assertNotNull(asset)
        assertEquals("antigravity-studio-1.0.0-macos-arm64.dmg", asset.name)
        assertEquals("https://github.com/dmg2", asset.downloadUrl)
    }

    @Test
    fun testResolvePlatformAssetMacOSX64() {
        val assets = listOf(
            ReleaseAsset(name = "antigravity-studio-1.0.0-macos-x64.dmg", downloadUrl = "https://github.com/dmg1"),
            ReleaseAsset(name = "antigravity-studio-1.0.0-macos-arm64.dmg", downloadUrl = "https://github.com/dmg2")
        )
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com", assets = assets)

        val asset = release.resolvePlatformAsset(osName = "Mac OS X", osArch = "x86_64")
        assertNotNull(asset)
        assertEquals("antigravity-studio-1.0.0-macos-x64.dmg", asset.name)
    }

    @Test
    fun testResolvePlatformAssetWindowsX64() {
        val assets = listOf(
            ReleaseAsset(name = "antigravity-studio-1.0.0-windows-x64.exe", downloadUrl = "https://github.com/win_exe"),
            ReleaseAsset(name = "antigravity-studio-1.0.0-windows-arm64.exe", downloadUrl = "https://github.com/win_arm")
        )
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com", assets = assets)

        val asset = release.resolvePlatformAsset(osName = "Windows 11", osArch = "amd64")
        assertNotNull(asset)
        assertEquals("antigravity-studio-1.0.0-windows-x64.exe", asset.name)
    }

    @Test
    fun testResolvePlatformAssetLinuxX64() {
        val assets = listOf(
            ReleaseAsset(name = "antigravity-studio-1.0.0-linux-x64.deb", downloadUrl = "https://github.com/deb"),
            ReleaseAsset(name = "antigravity-studio-1.0.0-linux-x64.tar.gz", downloadUrl = "https://github.com/tar")
        )
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com", assets = assets)

        val asset = release.resolvePlatformAsset(osName = "Linux", osArch = "x86_64")
        assertNotNull(asset)
        assertEquals("antigravity-studio-1.0.0-linux-x64.deb", asset.name)
    }

    @Test
    fun testResolvePlatformAssetUnmatchedPlatformReturnsNull() {
        val assets = listOf(
            ReleaseAsset(name = "antigravity-studio-1.0.0-macos-arm64.dmg", downloadUrl = "https://github.com/dmg2")
        )
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com", assets = assets)

        // FreeBSD / Solaris etc
        val asset = release.resolvePlatformAsset(osName = "FreeBSD", osArch = "x86_64")
        assertNull(asset, "Unrecognized OS must return null")
        assertNull(release.resolvePlatformDownloadUrl(osName = "FreeBSD", osArch = "x86_64"))
    }

    @Test
    fun testResolvePlatformAssetEmptyAssetsReturnsNull() {
        val release = ReleaseInfo(tagName = "v1.0.0", htmlUrl = "https://github.com/release/page", assets = emptyList())
        assertNull(release.resolvePlatformAsset(osName = "Mac OS X", osArch = "arm64"))
        assertNull(release.resolvePlatformDownloadUrl())
    }
}
