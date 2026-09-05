package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.app.AppHostManager
import com.yuzhiqiang.antigravity.host.model.ClientIntegrationState
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.EnvironmentOwner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppHostManagerTest {

    private lateinit var environment: HostTestEnvironment
    private lateinit var tempAppDir: File
    private lateinit var binDir: File

    @BeforeTest
    fun setUp() {
        environment = HostTestEnvironment()
        tempAppDir = File(environment.root, "AntigravityTestApp").apply { mkdirs() }
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        binDir = if (isWindows) {
            File(tempAppDir, "resources/bin").apply { mkdirs() }
        } else {
            File(tempAppDir, "Contents/Resources/bin").apply { mkdirs() }
        }
        File(tempAppDir, if (isWindows) "Antigravity.exe" else "Contents/MacOS/Antigravity").apply {
            parentFile.mkdirs()
            writeText("APP_BINARY")
        }
    }

    @AfterTest
    fun tearDown() {
        environment.close()
    }

    @Test
    fun testInstallAndRestoreLanguageServerShim() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")
        val endpointConfig = File(binDir, "language_server_endpoint.txt")

        // 1. 模拟初始原生二进制
        lsBinary.writeText("RAW_BINARY_CONTENT")
        assertTrue(lsBinary.exists())
        assertFalse(origBinary.exists())
        assertFalse(endpointConfig.exists())
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 2. 安装 Shim
        val installOk = AppHostManager.installLanguageServerShim(8330, tempAppDir.absolutePath)
        assertTrue(installOk)
        assertTrue(origBinary.exists(), "Original binary should be backed up")
        assertEquals("RAW_BINARY_CONTENT", origBinary.readText())
        assertTrue(lsBinary.exists(), "language_server binary / shim should exist")
        if (isWindows) {
            assertTrue(endpointConfig.exists(), "Endpoint config file should be created on Windows")
            assertEquals("http://127.0.0.1:8330", endpointConfig.readText().trim())
        } else {
            val shimContent = lsBinary.readText()
            assertTrue(shimContent.contains("ANTIGRAVITY_STUDIO_MANAGED_SHIM"))
            assertTrue(shimContent.contains("8330"))
        }
        assertTrue(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 3. 再次安装 Shim（幂等性）
        val secondInstallOk = AppHostManager.installLanguageServerShim(8335, tempAppDir.absolutePath)
        assertTrue(secondInstallOk)
        assertEquals("RAW_BINARY_CONTENT", origBinary.readText(), "Original binary should remain intact")
        if (isWindows) {
            assertEquals("http://127.0.0.1:8335", endpointConfig.readText().trim())
        } else {
            assertTrue(lsBinary.readText().contains("8335"))
        }

        // 4. 还原原始二进制
        val restoreOk = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restoreOk)
        assertTrue(lsBinary.exists(), "Original binary should be restored")
        assertEquals("RAW_BINARY_CONTENT", lsBinary.readText())
        assertFalse(origBinary.exists(), "Backup binary should be removed after restore")
        if (isWindows) {
            assertFalse(endpointConfig.exists(), "Endpoint config should be removed after restore on Windows")
        }
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
    }

    @Test
    fun testCustomExecutablePathResolvesToAppRoot() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val appExecutable = if (isWindows) {
            File(tempAppDir, "Antigravity.exe").apply { writeText("APP_BINARY") }
        } else {
            File(tempAppDir, "Contents/MacOS/Antigravity").apply {
                parentFile.mkdirs()
                writeText("APP_BINARY")
            }
        }
        val expectedRoot = tempAppDir.absoluteFile.normalize().path

        assertEquals(
            expectedRoot,
            AppHostManager.getCandidateInstallations(appExecutable.absolutePath).single().absoluteFile.normalize().path
        )

        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")
        lsBinary.writeText("RAW_BINARY_CONTENT")

        assertTrue(AppHostManager.installLanguageServerShim(8330, appExecutable.absolutePath))
        assertTrue(origBinary.exists())
        assertTrue(AppHostManager.isShimInstalled(appExecutable.absolutePath))
    }

    @Test
    fun testRestoreWhenOriginalExistsAndShimDeleted() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")

        // 模拟异常状态：lsBinary 不存在，仅 origBinary 存在
        origBinary.writeText("RECOVERABLE_CONTENT")
        assertTrue(origBinary.exists())
        assertFalse(lsBinary.exists())
        assertTrue(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 执行还原，验证自愈
        val restoreOk = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restoreOk)
        assertTrue(lsBinary.exists())
        assertEquals("RECOVERABLE_CONTENT", lsBinary.readText())
        assertFalse(origBinary.exists())
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
    }

    @Test
    fun testShimDetectionWhenNativeBinaryExists() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")

        // 原生 Mach-O / PE 二进制文件内容，不含 shim 标记
        lsBinary.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01))
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        // 即使由于残留存在 origBinary，只要 lsBinary 存在且不是 shim 脚本，执行 restore 会自愈清理 origBinary
        origBinary.writeText("EXTRA_BACKUP")
        val restoreOk = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restoreOk)
        assertTrue(lsBinary.exists())
        assertFalse(origBinary.exists())
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
    }

    @Test
    fun leftoverOriginalBackupIsNotProxyActive() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")
        origBinary.writeText("RECOVERABLE_CONTENT")

        assertTrue(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
        val status = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertFalse(status.isProxyActive)
        assertTrue(status.needsUpdate)
        assertEquals(ClientIntegrationState.MISMATCH, status.integrationState)
        assertFalse(AppHostManager.isActive(8330, tempAppDir.absolutePath))
    }

    @Test
    fun installedShimWithMatchingEnvironmentRequiresMigration() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isWindows && !isMac) return // 宿主环境变量接管仅适用于 macOS 与 Windows 平台

        if (isWindows) {
            File(tempAppDir, "Antigravity.exe").writeText("EXE")
        } else {
            File(tempAppDir, "Contents/MacOS/Antigravity").apply {
                parentFile.mkdirs()
                writeText("BIN")
            }
        }
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        lsBinary.writeText("RAW_BINARY_CONTENT")

        try {
            assertTrue(
                HostOwnershipStore.enableEnvironment(
                    EnvironmentOwner.APP,
                    8330
                ).isSuccess
            )
            assertTrue(AppHostManager.installLanguageServerShim(8330, tempAppDir.absolutePath))

            val status = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
            assertTrue(status.needsUpdate, "旧 Shim 即使匹配共享环境也必须迁移")
            assertEquals(ClientIntegrationState.MISMATCH, status.integrationState)
            assertFalse(status.isProxyActive, "旧 Shim 和共享环境不代表独立启动意图")
            assertFalse(AppHostManager.isActive(8330, tempAppDir.absolutePath))
        } finally {
            AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
            environment.endpoint = null
        }
    }

    @Test
    fun testInstalledShimWithMatchingEndpointAndEmptyEnvironmentRequiresMigration() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isWindows && !isMac) return

        if (isWindows) {
            File(tempAppDir, "Antigravity.exe").writeText("EXE")
        } else {
            File(tempAppDir, "Contents/MacOS/Antigravity").apply {
                parentFile.mkdirs()
                writeText("BIN")
            }
        }
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        lsBinary.writeText("RAW_BINARY_CONTENT")

        try {
            environment.endpoint = null
            assertTrue(AppHostManager.installLanguageServerShim(8330, tempAppDir.absolutePath))

            val status = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
            assertTrue(status.needsUpdate, "旧 Shim 端点匹配时仍必须迁移")
            assertEquals(ClientIntegrationState.MISMATCH, status.integrationState)
            assertFalse(status.isProxyActive, "Shim 端点不代表独立启动意图")
            assertEquals("http://127.0.0.1:8330", status.configuredEndpoint)

            val statusMismatched = AppHostManager.inspect(8335, isProxyRunning = true, tempAppDir.absolutePath)
            assertTrue(statusMismatched.needsUpdate, "Shim 端点与新代理端口不一致时应要求更新")
            assertEquals("http://127.0.0.1:8330", statusMismatched.configuredEndpoint)
        } finally {
            AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
            environment.endpoint = null
        }
    }

    @Test
    fun testInspectWhenAppNotInstalledAndCliEnabledDoesNotShowMismatch() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isWindows && !isMac) return

        try {
            assertTrue(HostOwnershipStore.enableLaunchIntegration(
                EnvironmentOwner.CLI,
                8330
            ).isSuccess)
            val nonExistentPath = File(environment.root, "non_existent_app_folder/Antigravity.app").absolutePath
            val status = AppHostManager.inspect(8330, isProxyRunning = true, customInstallation = nonExistentPath)
            assertFalse(status.isInstalled)
            assertEquals(ClientIntegrationState.OFFICIAL, status.integrationState)
            assertEquals(com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE, status.configurationState)
            assertFalse(status.needsUpdate)
            assertFalse(status.canDisable)
        } finally {
            environment.endpoint = null
        }
    }

    @Test
    fun testInspectWhenAppInstalledAndOnlyCliEnabledIsOfficialNotMismatch() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isWindows && !isMac) return

        if (isWindows) {
            File(tempAppDir, "Antigravity.exe").writeText("EXE")
        } else {
            File(tempAppDir, "Contents/MacOS/Antigravity").apply {
                parentFile.mkdirs()
                writeText("BIN")
            }
        }
        try {
            assertTrue(HostOwnershipStore.enableLaunchIntegration(
                EnvironmentOwner.CLI,
                8330
            ).isSuccess)
            val status = AppHostManager.inspect(8330, isProxyRunning = true, customInstallation = tempAppDir.absolutePath)
            assertTrue(status.isInstalled)
            assertEquals(ClientIntegrationState.OFFICIAL, status.integrationState)
            assertEquals(com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_ENABLED, status.configurationState)
            assertFalse(status.needsUpdate)
            assertFalse(status.canDisable)
        } finally {
            environment.endpoint = null
        }
    }

    @Test
    fun testNormalizeCustomInstallationWithSubdirectoryInsideAppBundle() {
        val fakeAppBundle = File(tempAppDir, "TestApp.app")
        val subDir = File(fakeAppBundle, "Contents/MacOS").apply { mkdirs() }
        val exe = File(subDir, "Antigravity").apply { writeText("BIN") }

        val normalizedFromSubDir = AppHostManager.getCandidateInstallations(subDir.absolutePath).single()
        assertEquals(fakeAppBundle.absoluteFile.normalize().path, normalizedFromSubDir.absoluteFile.normalize().path)

        val normalizedFromExe = AppHostManager.getCandidateInstallations(exe.absolutePath).single()
        assertEquals(fakeAppBundle.absoluteFile.normalize().path, normalizedFromExe.absoluteFile.normalize().path)
    }

    @Test
    fun testRestoreOriginalLanguageServerRestoresMissingLanguageServer() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")

        // 仅存在 original 备份，主二进制丢失
        origBinary.writeText("ORIGINAL_CONTENT")
        assertTrue(origBinary.exists())
        assertFalse(lsBinary.exists())

        val restored = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restored, "在仅存 original 时调用 restore 应成功自愈")
        assertTrue(lsBinary.exists(), "主二进制应被还原")
        assertEquals("ORIGINAL_CONTENT", lsBinary.readText())
        assertFalse(origBinary.exists(), "备份文件在恢复后应被清理")
        if (!isWindows) {
            assertTrue(lsBinary.canExecute(), "还原后的原生二进制应具有可执行权限")
        }
    }

    @Test
    fun testLeftoverOriginalBackupSelfHealsOnRestoreEvenIfShimInstalledWasTrue() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
        val origBinary =
            if (isWindows) File(binDir, "language_server.original.exe") else File(binDir, "language_server.original")

        origBinary.writeText("PERSISTENT_ORIGINAL")
        assertTrue(AppHostManager.isShimInstalled(tempAppDir.absolutePath))

        val statusBefore = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertTrue(statusBefore.needsUpdate)

        val restored = AppHostManager.restoreOriginalLanguageServer(tempAppDir.absolutePath)
        assertTrue(restored)
        assertTrue(lsBinary.exists())
        assertEquals("PERSISTENT_ORIGINAL", lsBinary.readText())
        assertFalse(origBinary.exists())

        val statusAfter = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertFalse(statusAfter.needsUpdate)
        assertEquals(ClientIntegrationState.OFFICIAL, statusAfter.integrationState)
    }

    @Test
    fun testInstallDetailedReturnsPermissionExceptionWhenDirectoryReadOnly() {
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isMac) return // 目录权限限制与只读 Shim 安装行为属于 macOS 专属测试
        val readOnlyDir = File(tempAppDir, "Contents/Resources/bin")
        readOnlyDir.setWritable(false, false)
        try {
            val result = AppHostManager.installLanguageServerShimDetailed(8330, tempAppDir.absolutePath)
            if (!readOnlyDir.canWrite()) {
                assertTrue(result.isFailure)
                val ex = result.exceptionOrNull()
                assertTrue(ex is AppHostManager.HostPermissionDeniedException || ex is SecurityException)
            }
        } finally {
            readOnlyDir.setWritable(true, false)
        }
    }

    @Test
    fun testMacLaunchIntegrationEnableAndDisable() {
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        if (!isMac) return

        File(tempAppDir, "Contents/MacOS/Antigravity").apply {
            parentFile.mkdirs()
            writeText("BIN")
        }
        val lsBinary = File(binDir, "language_server").apply {
            writeText("NATIVE_GO_BINARY")
        }

        try {
            val enableResult = AppHostManager.enableDetailed(8340, tempAppDir.absolutePath)
            assertTrue(enableResult.isSuccess, "macOS 下启用代理应成功且无需管理员权限")

            // 验证未破坏原生二进制
            assertEquals("NATIVE_GO_BINARY", lsBinary.readText(), "macOS 下不应篡改或替换原生 language_server 二进制")
            assertFalse(File(binDir, "language_server.original").exists(), "macOS 下不应生成 .original 备份")

            val status = AppHostManager.inspect(8340, isProxyRunning = true, tempAppDir.absolutePath)
            assertEquals(ClientIntegrationState.MANAGED, status.integrationState)
            assertFalse(status.needsUpdate)
            assertTrue(status.isProxyActive)

            assertTrue(AppHostManager.disable(tempAppDir.absolutePath))
            val disabledStatus = AppHostManager.inspect(8340, isProxyRunning = true, tempAppDir.absolutePath)
            assertEquals(ClientIntegrationState.OFFICIAL, disabledStatus.integrationState)
            assertFalse(disabledStatus.isProxyActive)
        } finally {
            AppHostManager.forceReset(tempAppDir.absolutePath)
        }
    }

    @Test
    fun enableDetailedRestoresOriginalOnlyBeforeEnablingLaunchIntegration() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = File(binDir, if (isWindows) "language_server.exe" else "language_server")
        val original = File(binDir, if (isWindows) "language_server.original.exe" else "language_server.original")
        original.writeText("RECOVERABLE_NATIVE_BINARY")
        environment.endpoint = "https://external.example.invalid"

        val before = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertEquals(ClientIntegrationState.MISMATCH, before.integrationState)
        assertFalse(AppHostManager.isActive(8330, tempAppDir.absolutePath))
        assertTrue(before.needsUpdate)

        assertTrue(AppHostManager.enableDetailed(8330, tempAppDir.absolutePath).isSuccess)
        assertEquals("RECOVERABLE_NATIVE_BINARY", lsBinary.readText())
        assertFalse(original.exists())
        assertFalse(AppHostManager.isShimInstalled(tempAppDir.absolutePath))
        if (!isWindows) assertTrue(lsBinary.canExecute())
        val after = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertEquals(ClientIntegrationState.MANAGED, after.integrationState)
        assertFalse(after.needsUpdate)
        assertTrue(after.isProxyActive)
        assertTrue(AppHostManager.isActive(8330, tempAppDir.absolutePath))
        assertEquals("https://external.example.invalid", environment.endpoint)
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
    }

    @Test
    fun enableDetailedRejectsShimWithoutOriginalAndDoesNotWriteLaunchReceipt() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        val lsBinary = File(binDir, if (isWindows) "language_server.exe" else "language_server")
        val original = File(binDir, if (isWindows) "language_server.original.exe" else "language_server.original")
        lsBinary.writeText("NATIVE_BINARY")
        assertTrue(AppHostManager.installLanguageServerShim(8330, tempAppDir.absolutePath))
        val shimContent = lsBinary.readBytes()
        assertTrue(original.delete())
        environment.endpoint = "https://external.example.invalid"

        val before = AppHostManager.inspect(8330, isProxyRunning = true, tempAppDir.absolutePath)
        assertEquals(ClientIntegrationState.MISMATCH, before.integrationState)
        assertTrue(before.needsUpdate)
        assertFalse(before.isProxyActive)

        assertTrue(AppHostManager.enableDetailed(8330, tempAppDir.absolutePath).isFailure)
        assertTrue(shimContent.contentEquals(lsBinary.readBytes()), "缺失备份时不得破坏现有文件")
        assertFalse(original.exists())
        assertEquals(null, HostOwnershipStore.configuredLaunchEndpoint(EnvironmentOwner.APP).getOrThrow())
        assertFalse(environment.root.resolve("receipts/host-launch-ownership.json").exists())
        assertFalse(AppHostManager.isActive(8330, tempAppDir.absolutePath))
        assertEquals("https://external.example.invalid", environment.endpoint)
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
    }

    @Test
    fun enableDisableAndResetPreserveCliAndSharedEnvironment() {
        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        File(binDir, if (isWindows) "language_server.exe" else "language_server").writeText("NATIVE_BINARY")
        environment.endpoint = "https://external.example.invalid"
        assertTrue(HostOwnershipStore.enableLaunchIntegration(EnvironmentOwner.CLI, 8340).isSuccess)

        for (reset in listOf(false, true)) {
            assertTrue(AppHostManager.enableDetailed(8330, tempAppDir.absolutePath).isSuccess)
            assertTrue(AppHostManager.isActive(8330, tempAppDir.absolutePath))
            assertEquals(ClientIntegrationState.MANAGED,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.CLI, 8340).state)
            assertTrue(if (reset) AppHostManager.forceReset(tempAppDir.absolutePath)
                else AppHostManager.disable(tempAppDir.absolutePath))
            assertFalse(AppHostManager.isActive(8330, tempAppDir.absolutePath))
            assertEquals(ClientIntegrationState.OFFICIAL,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.APP, 8330).state)
            assertEquals(ClientIntegrationState.MANAGED,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.CLI, 8340).state)
            assertEquals("https://external.example.invalid", environment.endpoint)
        }
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
    }

    @Ignore("真实应用安装诊断仅供手动运行，禁止自动测试修改应用")
    @Test
    fun diagnoseInstallOnRealAntigravityApp() {
        if (System.getenv("ANTIGRAVITY_DIAGNOSE_REAL_APP") != "1") {
            return
        }
        val appRoot = File("/Applications/Antigravity.app")
        if (!appRoot.isDirectory) {
            println("SKIP: /Applications/Antigravity.app 不存在")
            return
        }
        val binDir = File(appRoot, "Contents/Resources/bin")
        val lsFile = File(binDir, "language_server")
        val origFile = File(binDir, "language_server.original")
        println("BEFORE ls exists=${lsFile.exists()} size=${lsFile.length()} orig=${origFile.exists()} size=${origFile.length()}")
        println("BEFORE candidates=${AppHostManager.getCandidateInstallations(appRoot.absolutePath)}")
        println("BEFORE isShimInstalled=${AppHostManager.isShimInstalled(appRoot.absolutePath)}")
        val ok = AppHostManager.installLanguageServerShim(8321, appRoot.absolutePath)
        println("INSTALL ok=$ok")
        println("AFTER ls exists=${lsFile.exists()} size=${lsFile.length()} orig=${origFile.exists()} size=${origFile.length()}")
        println("AFTER isShimInstalled=${AppHostManager.isShimInstalled(appRoot.absolutePath)}")
        val head = runCatching {
            lsFile.inputStream().use { it.readNBytes(256).toString(Charsets.UTF_8) }
        }.getOrElse { it.message }
        println("AFTER head=$head")
        val status = AppHostManager.inspect(8321, isProxyRunning = true, appRoot.absolutePath)
        println("AFTER inspect active=${status.isProxyActive} needsUpdate=${status.needsUpdate} integration=${status.integrationState} config=${status.configurationState}")
    }
}
