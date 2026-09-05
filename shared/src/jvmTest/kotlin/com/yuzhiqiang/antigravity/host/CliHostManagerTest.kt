package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import com.yuzhiqiang.antigravity.host.model.ClientIntegrationState
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore.EnvironmentOwner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliHostManagerTest {
    private lateinit var environment: HostTestEnvironment

    @BeforeTest
    fun setUp() {
        environment = HostTestEnvironment()
    }

    @AfterTest
    fun tearDown() {
        environment.close()
    }

    @Test
    fun disablePreservesCliSettings() {
        assertSettingsPreserved { CliHostManager.disable() }
    }

    @Test
    fun forceResetPreservesCliSettings() {
        assertSettingsPreserved { CliHostManager.forceReset() }
    }

    @Test
    fun enableDisableAndResetPreserveAppAndSharedEnvironment() {
        environment.endpoint = "https://external.example.invalid"
        assertTrue(HostOwnershipStore.enableLaunchIntegration(EnvironmentOwner.APP, 8340).isSuccess)

        for (disable in listOf(CliHostManager::disable, CliHostManager::forceReset)) {
            assertTrue(CliHostManager.enable(8330))
            assertTrue(CliHostManager.isActive(8330))
            assertFalse(CliHostManager.isActive(8340))
            assertEquals(ClientIntegrationState.MANAGED,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.CLI, 8330).state)
            assertEquals(ClientIntegrationState.MANAGED,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.APP, 8340).state)
            assertTrue(disable())
            assertFalse(CliHostManager.isActive(8330))
            assertEquals(ClientIntegrationState.OFFICIAL,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.CLI, 8330).state)
            assertEquals(ClientIntegrationState.MANAGED,
                HostOwnershipStore.inspectLaunchIntegration(EnvironmentOwner.APP, 8340).state)
            assertEquals("https://external.example.invalid", environment.endpoint)
        }
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
    }

    @Test
    fun buildLaunchCommandRequiresEnabledIntentAndValidExecutable() {
        val executable = environment.root.resolve("agy").apply { writeText("#!/bin/sh\nexit 0\n") }
        executable.setExecutable(true)
        assertTrue(CliHostManager.buildLaunchCommand(8330, executable.absolutePath).isFailure)
        assertTrue(CliHostManager.enable(8330))
        for (port in listOf(0, -1, 65536)) {
            assertTrue(CliHostManager.buildLaunchCommand(port, executable.absolutePath).isFailure)
        }
        assertTrue(CliHostManager.buildLaunchCommand(8330, environment.root.resolve("missing").absolutePath).isFailure)
        assertTrue(CliHostManager.buildLaunchCommand(8330, environment.root.resolve("empty-bin").apply { mkdirs() }.absolutePath).isFailure)
        executable.setExecutable(false, false)
        if (!executable.canExecute()) {
            assertTrue(CliHostManager.buildLaunchCommand(8330, executable.absolutePath).isFailure)
        }
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
    }

    @Test
    fun buildLaunchCommandQuotesPathAndUsesCurrentPortWithoutMutatingEnvironment() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("mac") && !os.contains("linux")) return
        val executable = environment.root.resolve("custom dir/agy's cli").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\nexit 0\n")
            setExecutable(true)
        }
        environment.endpoint = "https://external.example.invalid"
        assertTrue(CliHostManager.enable(8330))

        // 1. preferShortLauncher = false 时，生成单次完整环境变量命令
        val command = CliHostManager.buildLaunchCommand(8340, executable.absolutePath, preferShortLauncher = false).getOrThrow()
        val quotedPath = "'" + executable.absolutePath.replace("'", "'\"'\"'") + "'"
        assertEquals(
            "env -u ANTIGRAVITY_LS_ADDRESS -u ANTIGRAVITY_CSRF_TOKEN -u ANTIGRAVITY_AGENT " +
                "-u ANTIGRAVITY_AGENTAPI_EXE CLOUD_CODE_URL='http://127.0.0.1:8340' $quotedPath",
            command
        )
        assertFalse(command.contains("export "))

        // 2. 默认 preferShortLauncher = true 且启动器就绪时，直接返回简洁的 agy-studio
        val launcherDir = environment.root.resolve("launcher-bin").apply { mkdirs() }
        CliHostManager.launcherDirectoryOverride = launcherDir
        try {
            val shortCommand = CliHostManager.buildLaunchCommand(8340, executable.absolutePath).getOrThrow()
            assertEquals("agy-studio", shortCommand)
        } finally {
            CliHostManager.launcherDirectoryOverride = null
        }

        assertEquals("http://127.0.0.1:8330", HostOwnershipStore.configuredLaunchEndpoint(EnvironmentOwner.CLI).getOrThrow())
        assertEquals("https://external.example.invalid", environment.endpoint)
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
        assertTrue(CliHostManager.disable())
        assertTrue(CliHostManager.buildLaunchCommand(8340, executable.absolutePath).isFailure)
    }

    @Test
    fun installAndUninstallLauncherManagesExecutableScript() {
        val launcherDir = environment.root.resolve("bin").apply { mkdirs() }
        CliHostManager.launcherDirectoryOverride = launcherDir
        try {
            val executable = environment.root.resolve("agy").apply {
                writeText("#!/bin/sh\nexit 0\n")
                setExecutable(true)
            }
            assertFalse(CliHostManager.isLauncherInstalled())

            val installedFile = CliHostManager.installLauncher(executable.absolutePath).getOrThrow()
            assertTrue(installedFile.exists())
            assertTrue(CliHostManager.isLauncherInstalled())

            val content = installedFile.readText()
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("win")) {
                assertTrue(content.contains("%*"))
                assertTrue(content.contains("CLOUD_CODE_URL=%ENDPOINT%"))
            } else {
                assertTrue(content.contains("\"$@\""))
                assertTrue(content.contains("CLOUD_CODE_URL=\"\$ENDPOINT\""))
                assertTrue(installedFile.canExecute())
            }

            assertTrue(CliHostManager.uninstallLauncher().isSuccess)
            assertFalse(installedFile.exists())
            assertFalse(CliHostManager.isLauncherInstalled())
        } finally {
            CliHostManager.launcherDirectoryOverride = null
        }
    }

    @Test
    fun windowsLauncherScriptsGenerateRobustCmdAndPowerShellSyntax() {
        val receipt = environment.root.resolve("host-launch-ownership.json")
        val fakeAgy = "C:\\Users\\test\\AppData\\Local\\agy\\bin\\agy.exe"

        // 1. 验证 CMD 脚本符合批处理隔离与退出码透传规范
        val cmdScript = CliHostManager.generateWindowsLauncherScript(fakeAgy, receipt)
        assertTrue(cmdScript.contains("setlocal enabledelayedexpansion"), "CMD 脚本必须开启延迟变量展开")
        assertTrue(cmdScript.contains("call \"!REAL_AGY!\" %*"), "CMD 脚本必须使用 call 兼容 exe/cmd 并通过 %* 透传所有参数")
        assertTrue(cmdScript.contains("set \"EXIT_CODE=%ERRORLEVEL%\""), "CMD 脚本必须捕获真实退出码")
        assertTrue(cmdScript.contains("endlocal & exit /b %EXIT_CODE%"), "CMD 脚本必须在退出前 endlocal 还原父终端环境并透传退出码")
        assertTrue(cmdScript.contains("set \"CLOUD_CODE_URL=!ENDPOINT!\""))

        // 2. 验证 PowerShell 脚本符合参数 Splatting 与环境 finally 还原规范
        val ps1Script = CliHostManager.generateWindowsPowerShellScript(fakeAgy, receipt)
        assertTrue(ps1Script.contains("[Parameter(ValueFromRemainingArguments = \$true)]"), "PS1 脚本必须捕获全部剩余参数")
        assertTrue(ps1Script.contains("& \$realAgy @ScriptArgs"), "PS1 脚本必须使用 @Splatting 完美透传参数")
        assertTrue(ps1Script.contains("exit \$LASTEXITCODE"), "PS1 脚本必须透传原生退出码")
        assertTrue(ps1Script.contains("finally {"), "PS1 脚本必须在 finally 块中还原父环境")
        assertTrue(ps1Script.contains("Remove-Item Env:ANTIGRAVITY_LS_ADDRESS"), "PS1 脚本必须清理会话变量")
    }

    @Test
    fun launcherScriptExecutesTargetWithTransparentArgumentsAndRejectsWhenDisabled() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("mac") && !os.contains("linux")) return

        val launcherDir = environment.root.resolve("bin").apply { mkdirs() }
        CliHostManager.launcherDirectoryOverride = launcherDir
        try {
            val argOutputFile = environment.root.resolve("args.json")
            val targetFakeAgy = environment.root.resolve("fake-agy").apply {
                writeText(
                    """
                    |#!/usr/bin/env bash
                    |cat <<EOF > "${argOutputFile.absolutePath}"
                    |{
                    |  "endpoint": "${'$'}CLOUD_CODE_URL",
                    |  "arg1": "${'$'}1",
                    |  "arg2": "${'$'}2",
                    |  "arg3": "${'$'}3",
                    |  "arg4": "${'$'}4"
                    |}
                    |EOF
                    |exit 42
                    |""".trimMargin()
                )
                setExecutable(true)
            }

            assertTrue(CliHostManager.enable(8335, targetFakeAgy.absolutePath))
            val launcherFile = CliHostManager.installLauncher(targetFakeAgy.absolutePath).getOrThrow()

            // 1. 运行 launcher，传递包含 --dangerously-skip-permissions 与带空格参数
            val process = ProcessBuilder(
                launcherFile.absolutePath,
                "--dangerously-skip-permissions",
                "-c",
                "-p",
                "hello from studio"
            ).start()
            val exitCode = process.waitFor()
            assertEquals(42, exitCode, "原生程序退出码应被 100% 透传")

            assertTrue(argOutputFile.exists())
            val recordedArgs = argOutputFile.readText()
            assertTrue(recordedArgs.contains("\"endpoint\": \"http://127.0.0.1:8335\""))
            assertTrue(recordedArgs.contains("\"arg1\": \"--dangerously-skip-permissions\""))
            assertTrue(recordedArgs.contains("\"arg2\": \"-c\""))
            assertTrue(recordedArgs.contains("\"arg3\": \"-p\""))
            assertTrue(recordedArgs.contains("\"arg4\": \"hello from studio\""))

            // 2. 停用代理后，launcher 应直接拦截并报错 exit 1
            assertTrue(CliHostManager.disable())
            val disabledProcess = ProcessBuilder(launcherFile.absolutePath, "--version").start()
            val disabledExit = disabledProcess.waitFor()
            val stderr = disabledProcess.errorStream.bufferedReader().readText()
            assertEquals(1, disabledExit)
            assertTrue(stderr.contains("[Studio]"))
        } finally {
            CliHostManager.launcherDirectoryOverride = null
        }
    }

    private fun assertSettingsPreserved(action: () -> Boolean) {
        val configFile = CliHostManager.getConfigFile()
        assertTrue(configFile.toPath().startsWith(environment.root.toPath()), "测试只能写入临时配置目录")
        val content = """{"theme":"dark","CLOUD_CODE_URL":"http://127.0.0.1:8321"}"""
        configFile.parentFile.mkdirs()
        configFile.writeText(content)

        assertTrue(action())

        assertTrue(configFile.isFile)
        assertEquals(content, configFile.readText())
    }
}
