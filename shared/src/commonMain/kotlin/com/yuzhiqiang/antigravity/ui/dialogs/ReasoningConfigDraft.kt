package com.yuzhiqiang.antigravity.ui.dialogs

import com.yuzhiqiang.antigravity.domain.model.ModelCapabilities
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningCapability
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMapping
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport

/** Provider 编辑器中单个模型的 reasoning 配置草稿。 */
data class ReasoningConfigDraft(
    val enabled: Boolean,
    val levels: Set<ReasoningLevel>,
    val customValue: String?,
    val thinkingBudget: Int?,
    val minThinkingBudget: Int?,
    val mappings: Map<ReasoningLevel, ReasoningMapping>,
    val configuredSupported: Boolean? = null,
    val defaultLevel: ReasoningLevel? = null,
    val type: String? = null
) {
    fun toCapability(
        protocol: ProviderProtocol,
        outputTokenLimit: Long?
    ): ReasoningCapability {
        if (!enabled) {
            val hasExplicitConfiguration = levels.isNotEmpty() || customValue != null ||
                    thinkingBudget != null || minThinkingBudget != null || configuredSupported != null
            return ReasoningCapability(
                supported = if (hasExplicitConfiguration) false else null,
                defaultLevel = defaultLevel,
                type = type
            )
        }
        val effectiveMappings = mappings
            .filterKeys { level -> level == ReasoningLevel.OFF || level in levels }
            .toMutableMap()
        levels.forEach { level ->
            val mapping = ReasoningMappingSupport.resolveMapping(
                protocol = protocol,
                level = level,
                configured = effectiveMappings,
                outputTokenLimit = outputTokenLimit
            )
            if (mapping != null) effectiveMappings[level] = mapping
        }
        val customMapping = customValue
            ?.takeIf { it.isNotBlank() }
            ?.let { value -> ReasoningMappingSupport.customMapping(protocol, value, outputTokenLimit) }
        when {
            customMapping != null -> effectiveMappings[ReasoningLevel.AUTO] = customMapping
            customValue == null && mappings[ReasoningLevel.AUTO]?.value == null -> {
                mappings[ReasoningLevel.AUTO]?.let { mapping ->
                    effectiveMappings[ReasoningLevel.AUTO] = mapping
                }
            }

            else -> effectiveMappings.remove(ReasoningLevel.AUTO)
        }
        return ReasoningCapability(
            supported = true,
            thinkingBudget = thinkingBudget.takeIf { protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT },
            minThinkingBudget = minThinkingBudget.takeIf { protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT },
            levels = ReasoningMappingSupport.encode(effectiveMappings).takeIf { it.isNotEmpty() },
            defaultLevel = defaultLevel,
            type = type
        )
    }

    companion object {
        fun fromCapabilities(capabilities: ModelCapabilities): ReasoningConfigDraft {
            val reasoning = capabilities.reasoning
            val mappings = ReasoningMappingSupport.parse(reasoning.levels)
            val levels = (mappings.keys + ReasoningMappingSupport.configuredLevels(reasoning.levels))
                .filterNot { level -> level == ReasoningLevel.OFF || level == ReasoningLevel.AUTO }
                .toSet()
            val customValue = mappings[ReasoningLevel.AUTO]
                ?.let(ReasoningMappingSupport::mappingValueAsString)
                ?: mappings[ReasoningLevel.AUTO]
                    ?.let(ReasoningMappingSupport::mappingValueAsInt)
                    ?.toString()
            return ReasoningConfigDraft(
                enabled = reasoning.supportsReasoning,
                levels = levels,
                customValue = customValue,
                thinkingBudget = reasoning.thinkingBudget,
                minThinkingBudget = reasoning.minThinkingBudget,
                mappings = mappings,
                configuredSupported = reasoning.supported,
                defaultLevel = reasoning.defaultLevel,
                type = reasoning.type
            )
        }
    }
}
