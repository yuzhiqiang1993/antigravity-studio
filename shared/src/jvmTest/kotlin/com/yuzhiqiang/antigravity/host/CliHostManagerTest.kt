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

        val command = CliHostManager.buildLaunchCommand(8340, executable.absolutePath).getOrThrow()
        val quotedPath = "'" + executable.absolutePath.replace("'", "'\"'\"'") + "'"
        assertEquals(
            "env -u ANTIGRAVITY_LS_ADDRESS -u ANTIGRAVITY_CSRF_TOKEN -u ANTIGRAVITY_AGENT " +
                "-u ANTIGRAVITY_AGENTAPI_EXE CLOUD_CODE_URL='http://127.0.0.1:8340' $quotedPath",
            command
        )
        assertFalse(command.contains("export "))
        assertEquals("http://127.0.0.1:8330", HostOwnershipStore.configuredLaunchEndpoint(EnvironmentOwner.CLI).getOrThrow())
        assertEquals("https://external.example.invalid", environment.endpoint)
        assertEquals(0, environment.environmentWrites)
        assertEquals(0, environment.environmentClears)
        assertTrue(CliHostManager.disable())
        assertTrue(CliHostManager.buildLaunchCommand(8340, executable.absolutePath).isFailure)
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
