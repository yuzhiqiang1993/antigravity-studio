package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ParameterOverrides(
    @SerialName("temperature")
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("extra_body")
    val extraBody: Map<String, JsonElement>? = null
) {
    /** 按 agy 的优先级合并参数；对象值递归合并，标量值由子层覆盖父层。 */
    fun mergeWith(child: ParameterOverrides?): ParameterOverrides {
        val mergedExtraBody = mergeExtraBody(extraBody.orEmpty(), child?.extraBody.orEmpty())
        return ParameterOverrides(
            temperature = child?.temperature ?: temperature,
            maxTokens = child?.maxTokens ?: maxTokens,
            topP = child?.topP ?: topP,
            topK = child?.topK ?: topK,
            extraBody = mergedExtraBody.takeIf { it.isNotEmpty() }
        )
    }

    /** 删除不得由 extra_body 覆盖的协议控制字段。 */
    fun withoutControlledExtraBody(): ParameterOverrides {
        val sanitized = extraBody.orEmpty().filterKeys { key ->
            key.lowercase() !in CONTROLLED_EXTRA_BODY_KEYS
        }
        return copy(extraBody = sanitized.takeIf { it.isNotEmpty() })
    }

    companion object {
        val CONTROLLED_EXTRA_BODY_KEYS: Set<String> = setOf(
            "model",
            "messages",
            "contents",
            "input",
            "instructions",
            "stream",
            "temperature",
            "max_tokens",
            "max_output_tokens",
            "top_p",
            "top_k",
            "reasoning",
            "reasoning_effort",
            "reasoning_budget_tokens",
            "thinking",
            "thinkingconfig",
            "thinking_config",
            "thinking_level",
            "thinkingbudget",
            "thinking_budget",
            "generationconfig",
            "generation_config",
            "output_config",
            "tools",
            "functions",
            "authorization",
            "api-key",
            "x-api-key"
        )

        private fun mergeExtraBody(
            parent: Map<String, JsonElement>,
            child: Map<String, JsonElement>
        ): Map<String, JsonElement> {
            if (parent.isEmpty()) return child.toMap()
            if (child.isEmpty()) return parent.toMap()
            val merged = parent.toMutableMap()
            child.forEach { (key, value) ->
                merged[key] = mergeJsonElement(merged[key], value)
            }
            return merged
        }

        private fun mergeJsonElement(parent: JsonElement?, child: JsonElement): JsonElement {
            if (parent !is JsonObject || child !is JsonObject) return child
            val merged = parent.toMutableMap()
            child.forEach { (key, value) ->
                merged[key] = mergeJsonElement(merged[key], value)
            }
            return JsonObject(merged)
        }
    }
}
