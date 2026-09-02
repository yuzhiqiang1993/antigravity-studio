package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
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
                    providerModelBindings = listOf(
                        ProviderModelBinding(
                            bindingId = "upstream",
                            providerConfigId = "provider",
                            providerModelId = "gpt-test",
                            displayName = "gpt-test"
                        )
                    ),
                    modelRouteVariants = listOf(
                        ModelRouteVariant(
                            variantId = "custom-gpt-test",
                            bindingId = "upstream",
                            catalogModelId = "custom-gpt-test",
                            runtimeModelId = "MODEL_PLACEHOLDER_M400",
                            displayName = "custom-gpt-test"
                        )
                    )
                )
            )
            val server = LocalProxyServer(store)
            val port = runBlocking { server.start(24_321).getOrThrow() }
            try {
                fun get(path: String, tokenHeader: String? = null): Pair<Int, String> {
                    val connection =
                        URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    if (tokenHeader != null) {
                        connection.setRequestProperty("X-Antigravity-Studio-Token", tokenHeader)
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
                }
                val (rootStatus, rootBody) = get("/")
                val (healthStatus, health) = get("/health")
                val (catalogStatus, catalog) = get("/v1/models")
                assertEquals(200, rootStatus)
                assertTrue(rootBody.contains("\"status\":\"ok\""))
                assertEquals(200, healthStatus)
                assertTrue(health.contains("\"status\":\"ok\""))
                assertEquals(200, catalogStatus)
                assertTrue(catalog.contains("custom-gpt-test"))

                val headConnection = URI("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
                headConnection.requestMethod = "HEAD"
                assertEquals(200, headConnection.responseCode)
            } finally {
                runBlocking { server.stop() }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
