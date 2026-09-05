package com.yuzhiqiang.antigravity.update

import com.sun.net.httpserver.HttpServer
import com.yuzhiqiang.antigravity.update.engine.UpdateChecker
import com.yuzhiqiang.antigravity.update.model.AppVersion
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.*

class UpdateCheckerTest {

    private val sampleLatestJson = """
        {
          "tag_name": "v1.3.4",
          "name": "Antigravity Studio v1.3.4",
          "body": "### 变更日志\n- 修复更新检查 403 Rate Limit 问题",
          "html_url": "https://github.com/yuzhiqiang1993/antigravity-studio/releases/tag/v1.3.4",
          "published_at": "2026-09-05T10:00:00Z",
          "prerelease": false,
          "draft": false,
          "assets": [
            {
              "name": "Antigravity-Studio-1.3.4-macos-arm64.dmg",
              "browser_download_url": "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v1.3.4/Antigravity-Studio-1.3.4-macos-arm64.dmg",
              "size": 1048576,
              "content_type": "application/octet-stream"
            },
            {
              "name": "Antigravity-Studio-1.3.4-macos-x64.dmg",
              "browser_download_url": "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v1.3.4/Antigravity-Studio-1.3.4-macos-x64.dmg",
              "size": 1048576,
              "content_type": "application/octet-stream"
            },
            {
              "name": "Antigravity-Studio-1.3.4-windows-x64.exe",
              "browser_download_url": "https://github.com/yuzhiqiang1993/antigravity-studio/releases/download/v1.3.4/Antigravity-Studio-1.3.4-windows-x64.exe",
              "size": 2097152,
              "content_type": "application/octet-stream"
            }
          ]
        }
    """.trimIndent()

    @AfterTest
    fun tearDown() {
        UpdateChecker.httpFetcher = null
    }

    @Test
    fun testCheckUpdateDetectsNewerVersion() = runBlocking {
        UpdateChecker.httpFetcher = { _, _ -> sampleLatestJson }

        val result = UpdateChecker.checkUpdate(currentVersion = "1.3.3")
        assertTrue(result.isSuccess)
        val release = result.getOrNull()
        assertNotNull(release)
        assertEquals("1.3.4", release.cleanVersion)
        assertEquals("v1.3.4", release.tagName)
        assertEquals(3, release.assets.size)
        assertTrue(release.body?.contains("403 Rate Limit") == true)

        val macAsset = release.resolvePlatformAsset(osName = "Mac OS X", osArch = "aarch64")
        assertNotNull(macAsset)
        assertEquals("Antigravity-Studio-1.3.4-macos-arm64.dmg", macAsset.name)
    }

    @Test
    fun testCheckUpdateReturnsNullWhenAlreadyUpToDate() = runBlocking {
        UpdateChecker.httpFetcher = { _, _ -> sampleLatestJson }

        val result = UpdateChecker.checkUpdate(currentVersion = "1.3.4")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun testCheckUpdateReturnsNullWhenCurrentIsAhead() = runBlocking {
        UpdateChecker.httpFetcher = { _, _ -> sampleLatestJson }

        val result = UpdateChecker.checkUpdate(currentVersion = "1.4.0")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun testCheckUpdateHandlesHttpError() = runBlocking {
        UpdateChecker.httpFetcher = { _, _ ->
            throw IllegalStateException("HTTP 404: Not Found")
        }

        val result = UpdateChecker.checkUpdate(currentVersion = "1.3.3")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun testCheckUpdateHandlesMalformedJson() = runBlocking {
        UpdateChecker.httpFetcher = { _, _ -> "{ corrupted json content" }

        val result = UpdateChecker.checkUpdate(currentVersion = "1.3.3")
        assertTrue(result.isFailure)
    }

    @Test
    fun testCheckUpdateRealHttp302RedirectFollow() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port

        server.createContext("/latest.json") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/step2.json")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/step2.json") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/final.json")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/final.json") { exchange ->
            val bytes = sampleLatestJson.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server.start()
        try {
            UpdateChecker.httpFetcher = null
            val result = UpdateChecker.checkUpdate(
                currentVersion = "1.3.3",
                targetEndpoint = "http://127.0.0.1:$port/latest.json"
            )
            assertTrue(result.isSuccess)
            val release = result.getOrNull()
            assertNotNull(release)
            assertEquals("1.3.4", release.cleanVersion)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testCheckUpdateFailsOnTooManyRedirects() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val port = server.address.port

        server.createContext("/loop") { exchange ->
            exchange.responseHeaders.set("Location", "http://127.0.0.1:$port/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }

        server.start()
        try {
            UpdateChecker.httpFetcher = null
            val result = UpdateChecker.checkUpdate(
                currentVersion = "1.3.3",
                targetEndpoint = "http://127.0.0.1:$port/loop"
            )
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Too many redirects") == true)
        } finally {
            server.stop(0)
        }
    }
}
