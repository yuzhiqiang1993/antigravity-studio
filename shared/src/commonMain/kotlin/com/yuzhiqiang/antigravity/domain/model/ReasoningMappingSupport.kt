package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 统一承载 agy reasoning mapping 的读取、默认映射与协议校验。
 *
 * levels 严格使用 { "low": { "kind": "effort", "value": "low" } } 形式。
 */
object ReasoningMappingSupport {
    fun configuredLevels(levels: JsonObject?): List<ReasoningLevel> {
        return parse(levels).keys.toList()
    }

    fun hasConfiguredLevels(levels: JsonObject?): Boolean {
        return !levels.isNullOrEmpty()
    }

    fun parse(levels: JsonObject?): Map<ReasoningLevel, ReasoningMapping> {
        return levels.orEmpty().map { (rawLevel, rawMapping) ->
            val level = ReasoningLevel.entries.firstOrNull { candidate -> levelKey(candidate) == rawLevel }
                ?: throw IllegalArgumentException("未知 reasoning level：$rawLevel")
            val mappingObject = rawMapping as? JsonObject
                ?: throw IllegalArgumentException("reasoning level $rawLevel 必须是对象")
            require(mappingObject.keys.all { key -> key == "kind" || key == "value" }) {
                "reasoning level $rawLevel 包含未知字段"
            }
            val kindElement = mappingObject["kind"] as? JsonPrimitive
                ?: throw IllegalArgumentException("reasoning level $rawLevel 缺少 kind")
            require(kindElement.isString && kindElement.content.isNotBlank()) {
                "reasoning level $rawLevel 的 kind 必须是非空字符串"
            }
            level to ReasoningMapping(
                kind = kindElement.content,
                value = mappingObject["value"]
            )
        }.toMap()
    }

    fun encode(mappings: Map<ReasoningLevel, ReasoningMapping>): JsonObject {
        return buildJsonObject {
            mappings.forEach { (level, mapping) ->
                put(levelKey(level), buildJsonObject {
                    put("kind", mapping.kind)
                    mapping.value?.let { value -> put("value", value) }
                })
            }
        }
    }

    fun defaultLevels(protocol: ProviderProtocol): List<ReasoningLevel> {
        return when (protocol) {
            ProviderProtocol.GEMINI_GENERATE_CONTENT -> listOf(
                ReasoningLevel.LOW,
                ReasoningLevel.MEDIUM,
                ReasoningLevel.HIGH
            )

            ProviderProtocol.ANTHROPIC_MESSAGES -> listOf(
                ReasoningLevel.LOW,
                ReasoningLevel.MEDIUM,
                ReasoningLevel.HIGH,
                ReasoningLevel.X_HIGH,
                ReasoningLevel.MAX,
                ReasoningLevel.ADAPTIVE
            )

            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> listOf(
                ReasoningLevel.LOW,
                ReasoningLevel.MEDIUM,
                ReasoningLevel.HIGH,
                ReasoningLevel.X_HIGH,
                ReasoningLevel.MAX
            )
        }
    }

    fun defaultMapping(protocol: ProviderProtocol, level: ReasoningLevel): ReasoningMapping? {
        return when (protocol) {
            ProviderProtocol.GEMINI_GENERATE_CONTENT -> when (level) {
                ReasoningLevel.OFF -> ReasoningMapping(kind = "disabled")
                ReasoningLevel.AUTO -> null
                else -> ReasoningMapping(
                    kind = "native_level",
                    value = JsonPrimitive(level.protocolValue())
                )
            }

            ProviderProtocol.ANTHROPIC_MESSAGES -> when (level) {
                ReasoningLevel.OFF -> ReasoningMapping(kind = "disabled")
                ReasoningLevel.AUTO -> null
                ReasoningLevel.ADAPTIVE -> ReasoningMapping(kind = "adaptive")
                else -> ReasoningMapping(
                    kind = "budget_tokens",
                    value = JsonPrimitive(level.anthropicBudget())
                )
            }

            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> when (level) {
                ReasoningLevel.OFF -> ReasoningMapping(kind = "disabled")
                ReasoningLevel.AUTO -> null
                else -> ReasoningMapping(
                    kind = "effort",
                    value = JsonPrimitive(level.protocolValue())
                )
            }
        }
    }

    fun resolveMapping(
        protocol: ProviderProtocol,
        level: ReasoningLevel,
        configured: Map<ReasoningLevel, ReasoningMapping>,
        outputTokenLimit: Long? = null
    ): ReasoningMapping? {
        val configuredMapping = configured[level]
        if (configuredMapping != null && isSupported(protocol, configuredMapping, outputTokenLimit)) {
            return configuredMapping
        }
        val suggested = defaultMapping(protocol, level)
        return suggested?.takeIf { mapping -> isSupported(protocol, mapping, outputTokenLimit) }
    }

    fun customMapping(
        protocol: ProviderProtocol,
        value: String,
        outputTokenLimit: Long? = null
    ): ReasoningMapping? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        if (protocol == ProviderProtocol.OPENAI_CHAT_COMPLETIONS ||
            protocol == ProviderProtocol.OPENAI_RESPONSES
        ) {
            return ReasoningMapping("effort", JsonPrimitive(normalized))
        }
        val numeric = normalized.toIntOrNull()
        if (numeric != null && normalized.all { it.isDigit() }) {
            if (numeric < 1_024) return null
            val mapping = ReasoningMapping("budget_tokens", JsonPrimitive(numeric))
            return mapping.takeIf { isSupported(protocol, it, outputTokenLimit) }
        }
        if (protocol == ProviderProtocol.ANTHROPIC_MESSAGES && normalized.equals("adaptive", true)) {
            return ReasoningMapping("adaptive")
        }
        if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
            return ReasoningMapping("native_level", JsonPrimitive(normalized))
        }
        if (protocol == ProviderProtocol.ANTHROPIC_MESSAGES) {
            return ReasoningMapping("effort", JsonPrimitive(normalized))
        }
        return null
    }

    fun mappingValueAsString(mapping: ReasoningMapping): String? {
        return mapping.value?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    fun mappingValueAsInt(mapping: ReasoningMapping): Int? {
        return mapping.value?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    }

    fun isSupported(
        protocol: ProviderProtocol,
        mapping: ReasoningMapping,
        outputTokenLimit: Long? = null
    ): Boolean {
        val kind = mapping.kind.lowercase()
        return when (protocol) {
            ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            ProviderProtocol.OPENAI_RESPONSES -> when (kind) {
                "disabled" -> true
                "effort" -> mappingValueAsString(mapping) != null
                else -> false
            }

            ProviderProtocol.GEMINI_GENERATE_CONTENT -> when (kind) {
                "disabled" -> true
                "native_level" -> mappingValueAsString(mapping) != null
                "budget_tokens" -> validBudget(mapping)
                else -> false
            }

            ProviderProtocol.ANTHROPIC_MESSAGES -> when (kind) {
                "adaptive", "disabled" -> true
                "effort" -> mappingValueAsString(mapping) != null
                "budget_tokens" -> {
                    val budget = mappingValueAsInt(mapping)
                    budget != null && budget >= 1_024 &&
                            (outputTokenLimit == null || budget.toLong() < outputTokenLimit)
                }

                else -> false
            }
        }
    }

    private fun validBudget(mapping: ReasoningMapping): Boolean {
        if (mapping.kind.lowercase() != "budget_tokens") return true
        return mappingValueAsInt(mapping)?.let { budget -> budget >= -1 } == true
    }

    private fun parseLevel(value: String): ReasoningLevel? {
        return when (value.lowercase().replace('-', '_')) {
            "off" -> ReasoningLevel.OFF
            "low" -> ReasoningLevel.LOW
            "medium" -> ReasoningLevel.MEDIUM
            "high" -> ReasoningLevel.HIGH
            "x_high", "xhigh" -> ReasoningLevel.X_HIGH
            "max" -> ReasoningLevel.MAX
            "adaptive" -> ReasoningLevel.ADAPTIVE
            "auto" -> ReasoningLevel.AUTO
            else -> null
        }
    }

    private fun levelKey(level: ReasoningLevel): String {
        return when (level) {
            ReasoningLevel.OFF -> "off"
            ReasoningLevel.LOW -> "low"
            ReasoningLevel.MEDIUM -> "medium"
            ReasoningLevel.HIGH -> "high"
            ReasoningLevel.X_HIGH -> "x_high"
            ReasoningLevel.MAX -> "max"
            ReasoningLevel.ADAPTIVE -> "adaptive"
            ReasoningLevel.AUTO -> "auto"
        }
    }

    private fun ReasoningLevel.protocolValue(): String {
        return when (this) {
            ReasoningLevel.X_HIGH -> "xhigh"
            else -> levelKey(this)
        }
    }

    private fun ReasoningLevel.anthropicBudget(): Int {
        return when (this) {
            ReasoningLevel.LOW -> 1_024
            ReasoningLevel.MEDIUM -> 4_096
            ReasoningLevel.HIGH -> 8_192
            ReasoningLevel.X_HIGH -> 16_384
            ReasoningLevel.MAX -> 32_768
            else -> 1_024
        }
    }
}
