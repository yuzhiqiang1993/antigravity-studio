package com.yuzhiqiang.antigravity.proxy.adapters

import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.proxy.model.NeutralChatRequest
import com.yuzhiqiang.antigravity.proxy.model.NeutralContent
import com.yuzhiqiang.antigravity.proxy.model.NeutralRole
import com.yuzhiqiang.antigravity.proxy.model.NeutralStreamChunk
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAI 兼容 Provider 的图片生成支持，统一回传为 Gemini inlineData。
 */
internal object OpenAiImageHandler {
    private val json = Json { ignoreUnknownKeys = true }

    fun sendImageGeneration(
        provider: Provider,
        request: NeutralChatRequest,
        authHeaders: Map<String, String>
    ): Flow<NeutralStreamChunk> = flow {
        val model = request.targetUpstreamModelId.removePrefix("models/")
        val url = imageGenerationUrl(provider, model)
        val prompt = request.messages.asReversed()
            .firstOrNull { message -> message.role == NeutralRole.USER }
            ?.contents
            ?.filterIsInstance<NeutralContent.Text>()
            ?.joinToString("\n") { text -> text.text }
            .orEmpty()
        if (prompt.isBlank()) {
            emit(NeutralStreamChunk.Error("Image generation requires a non-empty text prompt", 400))
            return@flow
        }

        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("response_format", "b64_json")
            imageSize(request.imageGenerationConfig)?.let { put("size", it) }
            (request.imageGenerationConfig as? JsonObject)
                ?.get("quality")
                ?.let { put("quality", it) }
            (request.imageGenerationConfig as? JsonObject)?.forEach { (key, value) ->
                if (key !in setOf("model", "prompt", "n", "response_format", "size", "quality", "aspectRatio", "aspect_ratio")) {
                    put(key, value)
                }
            }
        }.let { base -> ProviderAdapter.mergeSafeExtraBody(base, request) }

        try {
            val response = ProviderAdapter.executeStreamingWithTimeout(
                provider,
                minimumRequestTimeoutMs = 120_000L
            ) {
                ProviderAdapter.sharedHttpClient.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    ProviderAdapter.applyHeaders(this, provider, authHeaders)
                    ProviderAdapter.applyTimeouts(
                        this,
                        provider,
                        streaming = false,
                        minimumRequestTimeoutMs = 120_000L
                    )
                    setBody(body.toString())
                }.execute()
            }
            if (!response.status.isSuccess()) {
                OpenAiAdapterUtils.emitApiError(this, response, "OpenAI image", json)
                return@flow
            }
            val responseBody = ProviderAdapter.readResponseBodyText(response)
            if (responseBody.isFailure) {
                emit(NeutralStreamChunk.Error(responseBody.exceptionOrNull()?.message ?: "Failed to read OpenAI image response body", 502))
                return@flow
            }
            val root = json.parseToJsonElement(responseBody.getOrThrow()).jsonObject
            val data = root["data"]?.jsonArray.orEmpty()
            if (data.isEmpty()) {
                emit(NeutralStreamChunk.Error("OpenAI image response did not contain data", 502))
                return@flow
            }
            data.forEach { element ->
                val image = element.jsonObject
                val base64 = image["b64_json"]?.jsonPrimitive?.contentOrNull
                if (!base64.isNullOrBlank()) {
                    emit(NeutralStreamChunk.InlineDataDelta("image/png", base64.replace(Regex("\\s+"), "")))
                } else {
                    val imageUrl = image["url"]?.jsonPrimitive?.contentOrNull?.trim()
                    if (imageUrl.isNullOrBlank()) {
                        emit(NeutralStreamChunk.Error("OpenAI image response did not contain b64_json or url", 502))
                    } else if (imageUrl.startsWith("https://", ignoreCase = true) ||
                        imageUrl.startsWith("http://", ignoreCase = true) ||
                        imageUrl.startsWith("data:image/", ignoreCase = true)
                    ) {
                        // 与 byok 一致：URL 结果转为宿主可展示的 Markdown，避免
                        // 把外部 URL 当作代理响应地址而破坏后续请求路由。
                        emit(NeutralStreamChunk.TextDelta("![generated_image](" + imageUrl + ")"))
                    } else {
                        emit(NeutralStreamChunk.Error("OpenAI image response returned an unsafe URL", 502))
                    }
                }
            }
            emit(NeutralStreamChunk.Completed("stop"))
        } catch (error: Exception) {
            emit(NeutralStreamChunk.Error("OpenAI image request failed: " + (error.message ?: "unknown error"), ProviderAdapter.upstreamFailureStatus(error)))
        }
    }

    /** 从 chat/responses 同源端点推导 OpenAI images 端点，并保留原有 query 参数。 */
    private fun imageGenerationUrl(provider: Provider, model: String): String {
        val configured = provider.generateEndpoint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{model}", model)
            ?: return OpenAiAdapterUtils.normalizeUrl(provider.effectiveBaseUrl, "/images/generations")
        return runCatching {
            val uri = java.net.URI(configured)
            val path = uri.path.trimEnd('/')
            val targetPath = if (path.endsWith("/images/generations")) {
                path
            } else {
                val basePath = path
                    .removeSuffix("/chat/completions")
                    .removeSuffix("/responses")
                (if (basePath.isBlank()) "/v1" else basePath) + "/images/generations"
            }
            java.net.URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                targetPath,
                uri.query,
                uri.fragment
            ).toString()
        }.getOrElse {
            OpenAiAdapterUtils.normalizeUrl(provider.effectiveBaseUrl, "/images/generations")
        }
    }

    private fun imageSize(config: JsonElement?): String? {
        val objectConfig = config as? JsonObject ?: return null
        objectConfig["size"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        return when (objectConfig["aspectRatio"]?.jsonPrimitive?.contentOrNull
            ?: objectConfig["aspect_ratio"]?.jsonPrimitive?.contentOrNull) {
            "1:1" -> "1024x1024"
            "16:9" -> "1536x1024"
            "9:16" -> "1024x1536"
            "4:3" -> "1024x768"
            "3:4" -> "768x1024"
            "21:9" -> "1792x768"
            "9:21" -> "768x1792"
            else -> null
        }
    }
}
