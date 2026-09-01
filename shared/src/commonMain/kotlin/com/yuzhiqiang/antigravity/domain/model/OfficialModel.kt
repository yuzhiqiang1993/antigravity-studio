package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OfficialCatalogModel(
    @SerialName("catalog_model_id") val catalogModelId: String,
    @SerialName("runtime_model_id") val runtimeModelId: String? = null,
    @SerialName("provider_model_id") val providerModelId: String? = null,
    @SerialName("canonical_model_id") val canonicalModelId: String? = null,
    @SerialName("base_model_id") val baseModelId: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("catalog_api_provider") val catalogApiProvider: String? = null,
    @SerialName("provider_vendor") val providerVendor: String? = null,
    @SerialName("reasoning_profile") val reasoningProfile: ReasoningProfile? = null,
    @SerialName("identity_resolution") val identityResolution: ModelIdentityResolution = ModelIdentityResolution(),
    @SerialName("context_window") val contextWindow: Long? = null,
    @SerialName("max_tokens") val maxTokens: Long? = null,
    @SerialName("input_token_limit") val inputTokenLimit: Long? = null,
    @SerialName("output_token_limit") val outputTokenLimit: Long? = null,
    @SerialName("supports_vision") val supportsVision: Boolean = true,
    @SerialName("supports_tools") val supportsTools: Boolean = true,
    @SerialName("supports_reasoning") val supportsReasoning: Boolean = false,
    @SerialName("is_recommended") val isRecommended: Boolean = true,
    @SerialName("is_deprecated") val isDeprecated: Boolean = false,
    @SerialName("replacement_catalog_model_id") val replacementCatalogModelId: String? = null,
    @SerialName("agent_sort_order") val agentSortOrder: Long? = null,
    @SerialName("roles") val roles: List<String> = emptyList(),
    @SerialName("tier_group_ids") val tierGroupIds: List<String> = emptyList(),
    @SerialName("quota_info") val quotaInfo: OfficialModelQuotaInfo? = null,
    @SerialName("tags") val tags: OfficialModelTags? = null,
    @SerialName("raw_extra") val rawExtra: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class OfficialModelQuotaInfo(
    @SerialName("remaining_fraction") val remainingFraction: Double? = null,
    @SerialName("reset_time") val resetTime: String? = null
)

@Serializable
data class OfficialModelTags(
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class OfficialModelReplacement(
    @SerialName("catalog_model_id") val catalogModelId: String,
    @SerialName("replacement_catalog_model_id") val replacementCatalogModelId: String
)

@Serializable
data class OfficialCatalogSnapshot(
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("models") val models: List<OfficialCatalogModel> = emptyList(),
    @SerialName("replacements") val replacements: List<OfficialModelReplacement> = emptyList(),
    @SerialName("tier_groups") val tierGroups: Map<String, List<String>> = emptyMap(),
    @SerialName("default_agent_model_id") val defaultAgentModelId: String? = null,
    @SerialName("role_model_ids") val roleModelIds: Map<String, List<String>> = emptyMap(),
    @SerialName("fetched_at") val fetchedAt: Long = 0L
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
