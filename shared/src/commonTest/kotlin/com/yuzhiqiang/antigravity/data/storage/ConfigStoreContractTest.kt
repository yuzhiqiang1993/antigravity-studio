package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningCapability
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val testJson = Json { encodeDefaults = true }

class ConfigStoreContractTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("config-store-contract-test-")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun configWithoutCurrentSchemaIsClearedInsteadOfMigrated() {
        val configFile = tempDir.resolve("config.v1.json").toFile()
        configFile.writeText("""{"proxy_port": 9000}""")

        val store = ConfigStore(customRootDir = tempDir.toFile())

        assertEquals(8321, store.currentConfig.proxyPort)
        assertNotNull(store.loadError.value)
        assertTrue(store.loadError.value.orEmpty().contains("已清除并重置"))
        assertEquals(AppConfig.CURRENT_SCHEMA_VERSION, readSchemaVersion(configFile.toPath()))
    }

    @Test
    fun configWithUnknownFieldsIsClearedInsteadOfIgnored() {
        val configFile = tempDir.resolve("config.v1.json").toFile()
        configFile.writeText(
            """{"schema_version": 1, "proxy_port": 9000, "obsolete_option": true}"""
        )

        val store = ConfigStore(customRootDir = tempDir.toFile())

        assertEquals(8321, store.currentConfig.proxyPort)
        assertTrue(store.loadError.value.orEmpty().contains("已清除并重置"))
        assertEquals(AppConfig.CURRENT_SCHEMA_VERSION, readSchemaVersion(configFile.toPath()))
    }

    @Test
    fun invalidReasoningMappingClearsWholeConfig() {
        val provider = Provider(
            id = "provider",
            name = "Provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com/v1"
        )
        val upstream = UpstreamModel(
            id = "upstream",
            providerId = provider.id,
            upstreamModelId = "model",
            capabilities = ModelCapabilities(
                reasoning = ReasoningCapability(
                    supported = true,
                    levels = buildJsonObject {
                        put("ultra", buildJsonObject {
                            put("kind", "effort")
                            put("value", "high")
                        })
                    }
                )
            )
        )
        val config = AppConfig(
            providers = listOf(provider),
            upstreamModels = listOf(upstream),
            virtualModels = listOf(
                VirtualModel(
                    id = "virtual",
                    upstreamModelId = upstream.id,
                    hostModelId = "MODEL_PLACEHOLDER_M400"
                )
            )
        )
        tempDir.resolve("config.v1.json").toFile().writeText(
            testJson.encodeToString(AppConfig.serializer(), config)
        )

        val store = ConfigStore(customRootDir = tempDir.toFile())

        assertTrue(store.currentConfig.providers.isEmpty())
        assertTrue(store.loadError.value.orEmpty().contains("已清除并重置"))
    }

    @Test
    fun nonFileAccessFailureDoesNotDeleteTarget() {
        val configPath = tempDir.resolve("config.v1.json")
        Files.createDirectory(configPath)

        val store = ConfigStore(customRootDir = tempDir.toFile())

        assertTrue(Files.isDirectory(configPath))
        assertNotNull(store.loadError.value)
    }

    @Test
    fun danglingConfigSymlinkIsRemovedAndRebuilt() {
        val configPath = tempDir.resolve("config.v1.json")
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(configPath, tempDir.resolve("missing-config.json"))
        }.isSuccess
        if (!symlinkCreated) return

        ConfigStore(customRootDir = tempDir.toFile())

        assertTrue(Files.isRegularFile(configPath))
        assertTrue(!Files.isSymbolicLink(configPath))
        assertEquals(AppConfig.CURRENT_SCHEMA_VERSION, readSchemaVersion(configPath))
    }

    @Test
    fun upstreamWithoutVirtualModelIsRejected() {
        val store = ConfigStore(customRootDir = tempDir.toFile())
        val provider = Provider(
            id = "provider",
            name = "Provider",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com/v1"
        )

        assertFailsWith<IllegalArgumentException> {
            store.saveConfig(
                AppConfig(
                    providers = listOf(provider),
                    upstreamModels = listOf(
                        UpstreamModel(
                            id = "upstream",
                            providerId = provider.id,
                            upstreamModelId = "model"
                        )
                    )
                )
            )
        }
    }

    @Test
    fun currentSchemaLoadsWithoutCompatibilityProcessing() {
        val configFile = tempDir.resolve("config.v1.json").toFile()
        configFile.writeText(
            """{"schema_version": 1, "proxy_port": 9000, "language": "en", "theme_mode": "dark"}"""
        )
        tempDir.resolve("studio-settings.json").toFile().writeText(
            """{"language": "zh-CN", "theme_mode": "light"}"""
        )

        val store = ConfigStore(customRootDir = tempDir.toFile())

        assertEquals(9000, store.currentConfig.proxyPort)
        assertEquals("en", store.currentConfig.language)
        assertEquals("dark", store.currentConfig.themeMode)
        assertEquals(null, store.loadError.value)
    }

    private fun readSchemaVersion(path: Path): Int {
        return Json.parseToJsonElement(path.toFile().readText())
            .jsonObject
            .getValue("schema_version")
            .jsonPrimitive
            .int
    }
}
