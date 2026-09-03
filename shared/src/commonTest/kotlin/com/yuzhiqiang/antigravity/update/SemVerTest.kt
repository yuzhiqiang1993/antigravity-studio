package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.engine.SemVer
import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemVerTest {

    @Test
    fun testSemVerParsing() {
        val v1 = SemVer.parse("2.0.0")
        assertEquals(2, v1.major)
        assertEquals(0, v1.minor)
        assertEquals(0, v1.patch)
        assertEquals(null, v1.preRelease)

        val v2 = SemVer.parse("v2.1.3-beta.1")
        assertEquals(2, v2.major)
        assertEquals(1, v2.minor)
        assertEquals(3, v2.patch)
        assertEquals("beta.1", v2.preRelease)
    }

    @Test
    fun testSemVerComparison() {
        assertTrue(SemVer.isNewer("2.0.1", "2.0.0"))
        assertTrue(SemVer.isNewer("2.1.0", "2.0.9"))
        assertTrue(SemVer.isNewer("3.0.0", "2.99.99"))
        assertTrue(SemVer.isNewer("v2.0.1", "2.0.0"))
        assertTrue(SemVer.isNewer("2.0.0", "2.0.0-beta.1"))

        assertFalse(SemVer.isNewer("2.0.0", "2.0.0"))
        assertFalse(SemVer.isNewer("1.9.9", "2.0.0"))
        assertFalse(SemVer.isNewer("2.0.0-alpha", "2.0.0"))
    }

    @Test
    fun testReleaseAssetResolution() {
        val release = ReleaseInfo(
            tagName = "v2.0.1",
            name = "Release 2.0.1",
            body = "Bug fixes and improvements",
            htmlUrl = "https://github.com/yuzhiqiang1993/antigravity-studio/releases/tag/v2.0.1",
            assets = listOf(
                ReleaseAsset(
                    name = "Antigravity-Studio-2.0.1-macos-arm64.dmg",
                    downloadUrl = "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-macos-arm64.dmg"
                ),
                ReleaseAsset(
                    name = "Antigravity-Studio-2.0.1-macos-x64.dmg",
                    downloadUrl = "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-macos-x64.dmg"
                ),
                ReleaseAsset(
                    name = "Antigravity-Studio-2.0.1-windows-x64.exe",
                    downloadUrl = "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-windows-x64.exe"
                ),
                ReleaseAsset(
                    name = "Antigravity-Studio-2.0.1-linux-x64.deb",
                    downloadUrl = "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-linux-x64.deb"
                )
            )
        )

        assertEquals("2.0.1", release.cleanVersion)
        val downloadUrl = release.resolvePlatformDownloadUrl()
        assertTrue(!downloadUrl.isNullOrEmpty())
        assertEquals(
            "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-macos-arm64.dmg",
            release.resolvePlatformDownloadUrl(osName = "Mac OS X", osArch = "aarch64")
        )
        assertEquals(
            "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-windows-x64.exe",
            release.resolvePlatformDownloadUrl(osName = "Windows 11", osArch = "x86_64")
        )
        assertEquals(
            "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v2.0.1/Antigravity-Studio-2.0.1-linux-x64.deb",
            release.resolvePlatformDownloadUrl(osName = "Linux", osArch = "amd64")
        )
    }
}
