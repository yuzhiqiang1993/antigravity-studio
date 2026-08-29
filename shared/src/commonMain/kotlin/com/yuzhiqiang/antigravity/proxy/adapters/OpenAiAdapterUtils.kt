package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI 协议适配器公用工具方法。
 */
internal object OpenAiAdapterUtils {
    fun authHeaders(provider: Provider): Map<String, String> {
        return provider.apiKey
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer " + it) }
            ?: emptyMap()
    }

    fun normalizeUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val queryIndex = base.indexOf('?')
        if (queryIndex < 0) return if (base.endsWith(path)) base else base + path
        val pathPart = base.substring(0, queryIndex)
        val queryPart = base.substring(queryIndex)
        return if (pathPart.endsWith(path)) base else pathPart.trimEnd('/') + path + queryPart
    }

    suspend fun emitApiError(
        collector: FlowCollector<NeutralStreamChunk>,
        response: HttpResponse,
        providerName: String,
        json: Json
    ) {
        val bodyResult = ProviderAdapter.readLimitedResponseText(response)
        val rawBody = bodyResult.getOrElse { "<" + (it.message ?: "response body unavailable") + ">" }
        val extractedMessage = runCatching {
            val element = json.parseToJsonElement(rawBody)
            val jsonObject = element.jsonObject
            val errObj = jsonObject["error"]?.jsonObject
            errObj?.get("message")?.jsonPrimitive?.content ?: jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val displayMessage = extractedMessage ?: rawBody
        val status = bodyResult.exceptionOrNull()
            ?.let(ProviderAdapter::upstreamFailureStatus)
            ?: response.status.value
        collector.emit(
            NeutralStreamChunk.Error(
                providerName + " API error (" + response.status.value + "): " + displayMessage,
                status
            )
        )
    }
}
