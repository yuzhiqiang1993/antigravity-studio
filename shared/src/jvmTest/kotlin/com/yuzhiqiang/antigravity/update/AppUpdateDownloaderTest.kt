package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.engine.AppUpdateDownloader
import com.yuzhiqiang.antigravity.update.model.ReleaseAsset
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class AppUpdateDownloaderTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("downloader_test_").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testIsAllowedAssetUrlWhitelistedDomains() {
        // Official GitHub releases URL
        assertTrue(AppUpdateDownloader.isAllowedAssetUrl("https://github.com/org/repo/releases/download/v1.0.0/app.dmg"))
        // CDN object storage domains
        assertTrue(AppUpdateDownloader.isAllowedAssetUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/12345/app.dmg"))
        assertTrue(AppUpdateDownloader.isAllowedAssetUrl("https://release-assets.githubusercontent.com/123456/app-macos-arm64.dmg"))
        assertTrue(AppUpdateDownloader.isAllowedAssetUrl("https://github-releases.githubusercontent.com/123456/app-macos-arm64.dmg"))
        // Default 443 port
        assertTrue(AppUpdateDownloader.isAllowedAssetUrl("https://github.com:443/org/repo/releases/download/v1.0.0/app.dmg"))
    }

    @Test
    fun testIsAllowedAssetUrlRejectsInsecureAndUntrustedDomains() {
        // Plain HTTP must be rejected
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("http://github.com/org/repo/releases/download/v1.0.0/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("http://objects.githubusercontent.com/asset.dmg"))

        // Untrusted third-party domains
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://evil.com/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://malicious.org/github.com/app.dmg"))

        // Subdomain spoofing
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://github.com.evil.com/releases/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://objects.githubusercontent.com.attacker.com/app.dmg"))

        // Prefix spoofing
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://fake-github.com/releases/app.dmg"))

        // User info in URI
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://admin:password@github.com/releases/app.dmg"))

        // Non-standard port
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://github.com:8443/releases/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("https://github.com:80/releases/app.dmg"))

        // Invalid URIs / schemes
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("ftp://github.com/releases/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("file:///tmp/app.dmg"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("javascript:alert(1)"))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl(""))
        assertFalse(AppUpdateDownloader.isAllowedAssetUrl("   "))
    }

    @Test
    fun testResolveTargetFileSuccessAndSecuritySanitization() {
        val target = AppUpdateDownloader.resolveTargetFile("antigravity-studio-1.2.0-macos-arm64.dmg")
        assertEquals("antigravity-studio-1.2.0-macos-arm64.dmg", target.name)
        assertTrue(target.parentFile.exists())

        // Path traversal attempts must be rejected
        assertFailsWith<IllegalArgumentException> {
            AppUpdateDownloader.resolveTargetFile("../../evil.sh")
        }
        assertFailsWith<IllegalArgumentException> {
            AppUpdateDownloader.resolveTargetFile("/etc/shadow")
        }
        assertFailsWith<IllegalArgumentException> {
            AppUpdateDownloader.resolveTargetFile("subdir/nested.dmg")
        }
        assertFailsWith<IllegalArgumentException> {
            AppUpdateDownloader.resolveTargetFile("")
        }
    }

    @Test
    fun testDownloadRejectsExistingTargetFileWithoutOverwriting() = runBlocking {
        val existingFile = File(tempDir, "app.dmg")
        existingFile.writeText("existing precious user file content")

        val asset = ReleaseAsset(
            name = "app.dmg",
            downloadUrl = "https://github.com/org/repo/releases/download/v1.0.0/app.dmg",
            sizeBytes = 100L
        )

        val flow = AppUpdateDownloader.download(asset, "1.0.0", existingFile)

        val ex = assertFailsWith<IllegalArgumentException> {
            flow.toList()
        }
        assertTrue(ex.message!!.contains("already exists"))

        // Ensure original file was NOT touched or deleted
        assertEquals("existing precious user file content", existingFile.readText())
    }

    @Test
    fun testDownloadRejectsAssetNameMismatch() = runBlocking {
        val targetFile = File(tempDir, "app_different_name.dmg")
        val asset = ReleaseAsset(
            name = "app_original_name.dmg",
            downloadUrl = "https://github.com/org/repo/releases/download/v1.0.0/app_original_name.dmg",
            sizeBytes = 100L
        )

        val flow = AppUpdateDownloader.download(asset, "1.0.0", targetFile)

        val ex = assertFailsWith<IllegalArgumentException> {
            flow.toList()
        }
        assertTrue(ex.message!!.contains("Invalid update asset name"))
    }

    @Test
    fun testDownloadRejectsUntrustedAssetUrl() = runBlocking {
        val targetFile = File(tempDir, "app.dmg")
        val asset = ReleaseAsset(
            name = "app.dmg",
            downloadUrl = "https://untrusted-host.evil.com/app.dmg",
            sizeBytes = 100L
        )

        val flow = AppUpdateDownloader.download(asset, "1.0.0", targetFile)

        val ex = assertFailsWith<IllegalArgumentException> {
            flow.toList()
        }
        assertTrue(ex.message!!.contains("Untrusted update asset URL"))
    }
}
