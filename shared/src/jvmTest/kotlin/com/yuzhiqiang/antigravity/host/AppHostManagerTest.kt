package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.app.AppHostManager
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppHostManagerTest {

    private lateinit var tempAppDir: File
    private lateinit var binDir: File

    @BeforeTest
    fun setUp() {
        tempAppDir = File.createTempFile("AntigravityTestApp", "").apply {
            delete()
            mkdirs()
        }
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        binDir = if (isWindows) {
            File(tempAppDir, "resources/bin").apply { mkdirs() }
        } else {
            File(tempAppDir, "Contents/Resources/bin").apply { mkdirs() }
        }
    }

    @AfterTest
    fun tearDown() {
        tempAppDir.deleteRecursively()
    }

    @Test
    fun testInstallAndRestoreLanguageServerShim() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary = if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")

        // 1. 模拟初始原生二进制
        lsBinary.writeText("RAW_BINARY_CONTENT")
        assertTrue(lsBinary.exists())
        assertFalse(origBinary.exists())
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 2. 安装 Shim
        val installOk = AppHostManager.installLanguageServerShim(8330, tempAppDir.absolutePath)
        assertTrue(installOk)
        assertTrue(origBinary.exists(), "Original binary should be backed up")
        assertEquals("RAW_BINARY_CONTENT", origBinary.readText())
        assertTrue(lsBinary.exists(), "Shim script should be created")
        val shimContent = lsBinary.readText()
        assertTrue(shimContent.contains("ANTIGRAVITY_STUDIO_MANAGED_SHIM"))
        assertTrue(shimContent.contains("8330"))
        assertTrue(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 3. 再次安装 Shim（幂等性）
        val secondInstallOk = AppHostManager.installLanguageServerShim(8335, tempAppDir.absolutePath)
        assertTrue(secondInstallOk)
        assertEquals("RAW_BINARY_CONTENT", origBinary.readText(), "Original binary should remain intact")
        assertTrue(lsBinary.readText().contains("8335"))

        // 4. 还原原始二进制
        val restoreOk = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restoreOk)
        assertTrue(lsBinary.exists(), "Original binary should be restored")
        assertEquals("RAW_BINARY_CONTENT", lsBinary.readText())
        assertFalse(origBinary.exists(), "Backup binary should be removed after restore")
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
    }
}
