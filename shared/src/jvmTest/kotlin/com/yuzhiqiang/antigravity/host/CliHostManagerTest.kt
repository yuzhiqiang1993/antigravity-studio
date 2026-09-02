package com.yuzhiqiang.antigravity.host

import com.yuzhiqiang.antigravity.host.cli.CliHostManager
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliHostManagerTest {
    private lateinit var originalUserHome: String
    private lateinit var tempHome: java.io.File

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cli-host-manager-test-").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalUserHome)
        tempHome.deleteRecursively()
    }

    @Test
    fun disablePreservesCliSettings() {
        assertSettingsPreserved { CliHostManager.disable() }
    }

    @Test
    fun forceResetPreservesCliSettings() {
        assertSettingsPreserved { CliHostManager.forceReset() }
    }

    private fun assertSettingsPreserved(action: () -> Boolean) {
        val configFile = CliHostManager.getConfigFile()
        val content = """{"theme":"dark","CLOUD_CODE_URL":"http://127.0.0.1:8321"}"""
        configFile.parentFile.mkdirs()
        configFile.writeText(content)

        action()

        assertTrue(configFile.isFile)
        assertEquals(content, configFile.readText())
    }
}
