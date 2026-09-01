package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import com.yuzhiqiang.antigravity.proxy.model.normalizedNeutralUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object OfficialPassthroughJson {
    val catalog = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

internal data class OfficialSseObservation(
    val usage: NeutralUsage? = null,
    val responseModelId: String? = null,
    val hasMeaningfulContent: Boolean = false
)

internal object OfficialPassthroughUsage {
    fun parseResponseModelId(jsonElement: JsonElement): String? {
        val root = when (jsonElement) {
            is JsonObject -> jsonElement
            is JsonArray -> jsonElement.lastOrNull() as? JsonObject
            else -> null
        } ?: return null
        val response = root["response"] as? JsonObject
        return ((response?.get("modelVersion") ?: root["modelVersion"]) as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf(String::isNotBlank)
    }

    fun parseGeminiUsage(jsonElement: JsonElement): NeutralUsage? {
        val root = when (jsonElement) {
            is JsonObject -> jsonElement
            is JsonArray -> jsonElement.lastOrNull() as? JsonObject
            else -> null
        } ?: return null
        val effectiveRoot = (root["response"] as? JsonObject) ?: root
        val usage = (effectiveRoot["usageMetadata"] as? JsonObject)
            ?: (root["usageMetadata"] as? JsonObject)
            ?: return null

        fun long(vararg keys: String): Long? {
            for (key in keys) {
                val value = usage[key]?.jsonPrimitive?.longOrNull
                if (value != null) return value
            }
            return null
        }

        val prompt = long("promptTokenCount", "prompt_token_count")
        val cached = long("cachedContentTokenCount", "cached_content_token_count")
        val reasoning = long("thoughtsTokenCount", "thoughts_token_count")
        val output = long("candidatesTokenCount", "candidates_token_count")
        val validCacheBreakdown = prompt != null && prompt >= 0L &&
                (cached == null || cached in 0L..prompt)
        return normalizedNeutralUsage(
            inputTokens = prompt?.let { total -> if (validCacheBreakdown) total - (cached ?: 0L) else total },
            outputTokens = output,
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning,
            reportedTotalTokens = long("totalTokenCount", "total_token_count")
        )
    }

    fun extractObservationFromSseBuffer(
        buffer: StringBuilder,
        isFinal: Boolean = false
    ): OfficialSseObservation {
        var foundUsage: NeutralUsage? = null
        var responseModelId: String? = null
        var hasMeaningfulContent = false
        while (true) {
            val boundary = findEventBoundary(buffer)
            if (boundary != null) {
                val (eventEndIndex, delimiterLength) = boundary
                val rawEvent = buffer.substring(0, eventEndIndex)
                buffer.delete(0, eventEndIndex + delimiterLength)
                val observation = processRawSseEvent(rawEvent)
                observation.usage?.let { foundUsage = it }
                observation.responseModelId?.let { responseModelId = it }
                hasMeaningfulContent = hasMeaningfulContent || observation.hasMeaningfulContent
            } else if (isFinal && buffer.isNotEmpty()) {
                val rawEvent = buffer.toString()
                buffer.clear()
                val observation = processRawSseEvent(rawEvent)
                observation.usage?.let { foundUsage = it }
                observation.responseModelId?.let { responseModelId = it }
                hasMeaningfulContent = hasMeaningfulContent || observation.hasMeaningfulContent
                break
            } else {
                break
            }
        }
        return OfficialSseObservation(
            usage = foundUsage,
            responseModelId = responseModelId,
            hasMeaningfulContent = hasMeaningfulContent
        )
    }

    fun extractUsageFromSseBuffer(buffer: StringBuilder, isFinal: Boolean = false): NeutralUsage? {
        return extractObservationFromSseBuffer(buffer, isFinal).usage
    }

    private fun findEventBoundary(buffer: StringBuilder): Pair<Int, Int>? {
        val lfIndex = buffer.indexOf("\n\n")
        val crlfIndex = buffer.indexOf("\r\n\r\n")
        return when {
            lfIndex < 0 && crlfIndex < 0 -> null
            crlfIndex >= 0 && (lfIndex < 0 || crlfIndex < lfIndex) -> crlfIndex to 4
            else -> lfIndex to 2
        }
    }

    private fun processRawSseEvent(rawEvent: String): OfficialSseObservation {
        val dataLines = rawEvent.lineSequence()
            .map { it.removeSuffix("\r").trimStart() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trimStart() }
            .toList()
        val data = if (dataLines.isNotEmpty()) {
            dataLines.joinToString("\n").trim()
        } else {
            rawEvent.trim().takeIf { it.startsWith("{") || it.startsWith("[") }.orEmpty()
        }
        if (data.isEmpty() || data == "[DONE]") return OfficialSseObservation()

        val jsonElement = runCatching {
            OfficialPassthroughJson.catalog.parseToJsonElement(data)
        }.getOrNull() ?: return OfficialSseObservation()
        return OfficialSseObservation(
            usage = runCatching { parseGeminiUsage(jsonElement) }.getOrNull(),
            responseModelId = runCatching { parseResponseModelId(jsonElement) }.getOrNull(),
            hasMeaningfulContent = runCatching { containsMeaningfulContent(jsonElement) }.getOrDefault(false)
        )
    }

    internal fun containsMeaningfulContent(jsonElement: JsonElement): Boolean {
        val roots = when (jsonElement) {
            is JsonObject -> listOf(jsonElement)
            is JsonArray -> jsonElement.filterIsInstance<JsonObject>()
            else -> emptyList()
        }
        return roots.any { root ->
            val payload = (root["response"] as? JsonObject) ?: root
            containsMeaningfulCandidate(payload)
        }
    }

    private fun containsMeaningfulCandidate(payload: JsonObject): Boolean {
        val candidates = payload["candidates"] as? JsonArray ?: return false
        return candidates.filterIsInstance<JsonObject>().any { candidate ->
            val content = candidate["content"] as? JsonObject ?: return@any false
            val parts = content["parts"] as? JsonArray ?: return@any false
            parts.filterIsInstance<JsonObject>().any(::isMeaningfulPart)
        }
    }

    private fun isMeaningfulPart(part: JsonObject): Boolean {
        val text = (part["text"] as? JsonPrimitive)?.contentOrNull
        val signature = (part["thoughtSignature"] as? JsonPrimitive)?.contentOrNull
            ?: (part["thought_signature"] as? JsonPrimitive)?.contentOrNull
        return !text.isNullOrEmpty() ||
                !signature.isNullOrEmpty() ||
                listOf(
                    "functionCall",
                    "function_call",
                    "inlineData",
                    "inline_data",
                    "executableCode",
                    "codeExecutionResult"
                ).any(part::containsKey)
    }
}
