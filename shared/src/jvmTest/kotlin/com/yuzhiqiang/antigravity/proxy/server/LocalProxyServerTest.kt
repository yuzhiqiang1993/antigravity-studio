package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.UpstreamModel
import com.yuzhiqiang.antigravity.domain.model.VirtualModel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalProxyServerTest {

    @Test
    fun localProxyExposesHealthAndCustomCatalog() {
        val root = File.createTempFile("studio-parity-", ".dir").apply {
            delete()
            mkdirs()
        }
        try {
            val store = ConfigStore(root)
            store.saveConfig(
                AppConfig(
                    providers = listOf(
                        Provider(
                            id = "provider",
                            name = "Provider",
                            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                            baseUrl = "https://example.com/v1",
                            modelsEndpoint = "https://example.com/v1/models",
                            generateEndpoint = "https://example.com/v1/chat/completions"
                        )
                    ),
                    upstreamModels = listOf(
                        UpstreamModel(
                            id = "upstream",
                            providerId = "provider",
                            upstreamModelId = "gpt-test"
                        )
                    ),
                    virtualModels = listOf(
                        VirtualModel(
                            id = "custom-gpt-test",
                            upstreamModelId = "upstream",
                            hostModelId = "MODEL_PLACEHOLDER_M400"
                        )
                    )
                )
            )
            val server = LocalProxyServer(store)
            val port = runBlocking { server.start(24_321).getOrThrow() }
            try {
                fun get(path: String): Pair<Int, String> {
                    val connection =
                        URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    return connection.responseCode to connection.inputStream.bufferedReader().use { it.readText() }
                }
                val (healthStatus, health) = get("/health")
                val (catalogStatus, catalog) = get("/v1/models")
                assertEquals(200, healthStatus)
                assertTrue(health.contains("\"status\":\"ok\""))
                assertEquals(200, catalogStatus)
                assertTrue(catalog.contains("custom-gpt-test"))
            } finally {
                runBlocking { server.stop() }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
