package com.yuzhiqiang.antigravity.ui.dialogs.provider

import com.yuzhiqiang.antigravity.domain.model.*

data class CatalogModelConfig(
    val id: String,
    val name: String,
    val vendor: String? = null,
    val inputTokenLimit: Long? = null,
    val inputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val outputTokenLimit: Long? = null,
    val outputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    val isVision: Boolean = false,
    val inputModalities: Set<ModelModality> = emptySet(),
    val outputModalities: Set<ModelModality> = emptySet(),
    val inputMimeTypes: List<String> = emptyList(),
    val roles: Set<ModelRole> = emptySet(),
    val isImageGeneration: Boolean = false,
    val compressionPolicy: ModelCompressionPolicy? = null,
    val reasoningMappings: Map<ReasoningLevel, ReasoningMapping> = emptyMap(),
    val isReasoning: Boolean = false,
    val reasoningDraft: com.yuzhiqiang.antigravity.ui.dialogs.ReasoningConfigDraft =
        com.yuzhiqiang.antigravity.ui.dialogs.ReasoningConfigDraft(
            enabled = false,
            levels = emptySet(),
            customValue = null,
            thinkingBudget = null,
            minThinkingBudget = null,
            mappings = emptyMap()
        ),
    val isTools: Boolean = true,
    val isUnavailable: Boolean = false,
    val testStatusText: String? = null,
    val isTestSuccess: Boolean = true,
    val isTesting: Boolean = false,
    val testErrorMessage: String? = null,
    val testStatusCode: Int? = null,
    val testLatencyMs: Long? = null
)

