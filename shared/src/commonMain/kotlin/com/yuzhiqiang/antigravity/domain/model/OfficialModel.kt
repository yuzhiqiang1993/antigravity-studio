package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OfficialCatalogModel(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("context_window") val contextWindow: Long? = null,
    @SerialName("max_tokens") val maxTokens: Long? = null,
    @SerialName("input_token_limit") val inputTokenLimit: Long? = null,
    @SerialName("output_token_limit") val outputTokenLimit: Long? = null,
    @SerialName("supports_vision") val supportsVision: Boolean = true,
    @SerialName("supports_tools") val supportsTools: Boolean = true,
    @SerialName("supports_reasoning") val supportsReasoning: Boolean = false,
    @SerialName("is_recommended") val isRecommended: Boolean = true,
    @SerialName("is_deprecated") val isDeprecated: Boolean = false,
    @SerialName("replacement_model_id") val replacementModelId: String? = null,
    @SerialName("agent_sort_order") val agentSortOrder: Long? = null,
    @SerialName("roles") val roles: List<String> = emptyList()
)
