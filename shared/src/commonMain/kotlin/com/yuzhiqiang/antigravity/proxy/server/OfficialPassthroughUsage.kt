package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object OfficialPassthroughJson {
    val catalog = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

internal object OfficialPassthroughUsage {
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
        val validCacheBreakdown = prompt != null && (cached ?: 0L) <= prompt
        val validReasoningBreakdown = output != null && (reasoning ?: 0L) <= output
        val computedTotal = prompt?.plus((output ?: 0L) + (reasoning ?: 0L))
        val reportedTotal = long("totalTokenCount", "total_token_count")
        return NeutralUsage(
            inputTokens = prompt?.let { total -> if (validCacheBreakdown) total - (cached ?: 0L) else total },
            outputTokens = output?.let { total -> if (validReasoningBreakdown) total - (reasoning ?: 0L) else total },
            cacheReadTokens = cached.takeIf { validCacheBreakdown },
            reasoningTokens = reasoning.takeIf { validReasoningBreakdown },
            totalTokens = reportedTotal?.takeIf { computedTotal == null || it >= computedTotal } ?: computedTotal
        )
    }

    fun extractUsageFromSseBuffer(buffer: StringBuilder, isFinal: Boolean = false): NeutralUsage? {
        var foundUsage: NeutralUsage? = null
        while (true) {
            val eventEndIndex = buffer.indexOf("\n\n")
            if (eventEndIndex >= 0) {
                val rawEvent = buffer.substring(0, eventEndIndex)
                buffer.delete(0, eventEndIndex + 2)
                processRawSseEvent(rawEvent)?.let { foundUsage = it }
            } else if (isFinal && buffer.isNotEmpty()) {
                val rawEvent = buffer.toString()
                buffer.clear()
                processRawSseEvent(rawEvent)?.let { foundUsage = it }
                break
            } else {
                break
            }
        }
        return foundUsage
    }

    private fun processRawSseEvent(rawEvent: String): NeutralUsage? {
        var eventUsage: NeutralUsage? = null
        rawEvent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("data:")) {
                val data = trimmed.removePrefix("data:").trim()
                if (data.isNotEmpty() && data != "[DONE]") {
                    val parsedUsage = runCatching {
                        val jsonElement = OfficialPassthroughJson.catalog.parseToJsonElement(data)
                        parseGeminiUsage(jsonElement)
                    }.getOrNull()
                    if (parsedUsage != null) {
                        eventUsage = parsedUsage
                    }
                }
            }
        }
        return eventUsage
    }
}
