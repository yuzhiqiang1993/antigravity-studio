package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ReasoningLevel {
    @SerialName("off")
    OFF,

    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH,

    @SerialName("x_high")
    X_HIGH,

    @SerialName("max")
    MAX,

    @SerialName("adaptive")
    ADAPTIVE,

    @SerialName("auto")
    AUTO;

    val label: String
        get() = when (this) {
            OFF -> "Off"
            LOW -> "Low"
            MEDIUM -> "Medium"
            HIGH -> "High"
            X_HIGH -> "X-High"
            MAX -> "Max"
            ADAPTIVE -> "Adaptive"
            AUTO -> "Auto"
        }
}

@Serializable
enum class ModelRole {
    @SerialName("agent")
    AGENT,

    @SerialName("command")
    COMMAND,

    @SerialName("tab")
    TAB,

    @SerialName("image_generation")
    IMAGE_GENERATION,

    @SerialName("mquery")
    MQUERY,

    @SerialName("web_search")
    WEB_SEARCH,

    @SerialName("commit_message")
    COMMIT_MESSAGE,

    @SerialName("audio_transcription")
    AUDIO_TRANSCRIPTION
}

@Serializable
enum class ModelModality {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    @SerialName("audio")
    AUDIO,

    @SerialName("video")
    VIDEO,

    @SerialName("document")
    DOCUMENT
}

/** agy 的 reasoning.levels 值；value 可按 mapping kind 为字符串或数字。 */
@Serializable
data class ReasoningMapping(
    @SerialName("kind") val kind: String = "disabled",
    @SerialName("value") val value: JsonElement? = null
)

@Serializable
data class ReasoningCapability(
    @SerialName("supported") val supported: Boolean? = null,
    @SerialName("thinking_budget") val thinkingBudget: Int? = null,
    @SerialName("min_thinking_budget") val minThinkingBudget: Int? = null,
    @SerialName("levels") val levels: JsonObject? = null,
    @SerialName("type") val type: String? = null
) {
    val supportsReasoning: Boolean
        get() {
            return when (supported) {
                true -> true
                false -> false
                null -> thinkingBudget != null || minThinkingBudget != null ||
                        ReasoningMappingSupport.hasConfiguredLevels(levels)
            }
        }
}

@Serializable
data class ModelCapabilities(
    @SerialName("roles") val roles: List<ModelRole> = listOf(ModelRole.AGENT),
    @SerialName("input_modalities") val inputModalities: List<ModelModality> = listOf(ModelModality.TEXT),
    @SerialName("output_modalities") val outputModalities: List<ModelModality> = listOf(ModelModality.TEXT),
    @SerialName("tools") val tools: Boolean = false,
    @SerialName("input_mime_types") val inputMimeTypes: List<String> = emptyList(),
    @SerialName("reasoning") val reasoning: ReasoningCapability = ReasoningCapability()
) {
    val supportsVision: Boolean
        get() = ModelModality.IMAGE in inputModalities
}

@Serializable
enum class TokenLimitSource {
    @SerialName("catalog")
    CATALOG,

    @SerialName("configured")
    CONFIGURED,

    @SerialName("estimated")
    ESTIMATED,

    @SerialName("unknown")
    UNKNOWN
}

@Serializable
data class ModelTokenLimits(
    @SerialName("context_window") val contextWindow: Long? = null,
    @SerialName("context_window_source") val contextWindowSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    @SerialName("input_token_limit") val inputTokenLimit: Long? = null,
    @SerialName("input_token_limit_source") val inputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN,
    @SerialName("output_token_limit") val outputTokenLimit: Long? = null,
    @SerialName("output_token_limit_source") val outputTokenLimitSource: TokenLimitSource = TokenLimitSource.UNKNOWN
)


@Serializable
enum class ModelSource {
    @SerialName("official")
    OFFICIAL,

    @SerialName("byok")
    BYOK
}

@Serializable
enum class ModelIdentityStatus {
    @SerialName("resolved")
    RESOLVED,

    @SerialName("unresolved")
    UNRESOLVED,

    @SerialName("conflict")
    CONFLICT
}

@Serializable
enum class ModelIdentitySource {
    @SerialName("official_provider_model")
    OFFICIAL_PROVIDER_MODEL,

    @SerialName("provider_catalog")
    PROVIDER_CATALOG,

    @SerialName("provider_response")
    PROVIDER_RESPONSE,

    @SerialName("user_configured")
    USER_CONFIGURED,

    @SerialName("registered_alias")
    REGISTERED_ALIAS,

    @SerialName("route_snapshot")
    ROUTE_SNAPSHOT,

    @SerialName("unknown")
    UNKNOWN
}

@Serializable
enum class ModelIdentityConfidence {
    @SerialName("exact")
    EXACT,

    @SerialName("registered")
    REGISTERED,

    @SerialName("unknown")
    UNKNOWN
}

@Serializable
data class ModelIdentityResolution(
    @SerialName("status") val status: ModelIdentityStatus = ModelIdentityStatus.UNRESOLVED,
    @SerialName("source") val source: ModelIdentitySource = ModelIdentitySource.UNKNOWN,
    @SerialName("confidence") val confidence: ModelIdentityConfidence = ModelIdentityConfidence.UNKNOWN
)

@Serializable
enum class ModelAliasKind {
    @SerialName("provider_response")
    PROVIDER_RESPONSE,

    @SerialName("provider_request")
    PROVIDER_REQUEST,

    @SerialName("pricing")
    PRICING
}

@Serializable
data class ModelAlias(
    @SerialName("value") val value: String,
    @SerialName("kind") val kind: ModelAliasKind,
    @SerialName("provider_vendor") val providerVendor: String? = null
)

@Serializable
data class CanonicalModel(
    @SerialName("canonical_model_id") val canonicalModelId: String,
    @SerialName("provider_vendor") val providerVendor: String,
    @SerialName("base_model_id") val baseModelId: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("pricing_aliases") val pricingAliases: List<String> = emptyList()
)

@Serializable
data class ProviderModelBinding(
    @SerialName("binding_id") val bindingId: String,
    @SerialName("provider_config_id") val providerConfigId: String,
    @SerialName("provider_model_id") val providerModelId: String,
    @SerialName("canonical_model_id") val canonicalModelId: String? = null,
    @SerialName("provider_vendor") val providerVendor: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("aliases") val aliases: List<ModelAlias> = emptyList(),
    @SerialName("identity_resolution") val identityResolution: ModelIdentityResolution = ModelIdentityResolution(),
    @SerialName("source") val source: ModelSource = ModelSource.BYOK,
    @SerialName("capabilities") val capabilities: ModelCapabilities = ModelCapabilities(),
    @SerialName("token_limits") val tokenLimits: ModelTokenLimits = ModelTokenLimits(),
    @SerialName("compression_policy") val compressionPolicy: ModelCompressionPolicy? = null,
    @SerialName("tokenizer") val tokenizer: JsonObject? = null,
    @SerialName("parameter_overrides") val parameterOverrides: ParameterOverrides = ParameterOverrides(),
    @SerialName("enabled") val enabled: Boolean = true
) {
    val effectiveName: String
        get() = displayName.ifBlank { providerModelId }
}

@Serializable
enum class ModelVariantKind {
    @SerialName("direct")
    DIRECT,

    @SerialName("reasoning_variant")
    REASONING_VARIANT,

    @SerialName("tiered")
    TIERED
}

@Serializable
data class ReasoningProfile(
    @SerialName("level") val level: ReasoningLevel? = null,
    @SerialName("mapping") val mapping: ReasoningMapping? = null,
    @SerialName("budget_tokens") val budgetTokens: Int? = null,
    @SerialName("min_budget_tokens") val minBudgetTokens: Int? = null,
    @SerialName("source") val source: ModelIdentitySource = ModelIdentitySource.UNKNOWN
)

@Serializable
data class ModelRouteVariant(
    @SerialName("variant_id") val variantId: String,
    @SerialName("binding_id") val bindingId: String? = null,
    @SerialName("catalog_model_id") val catalogModelId: String,
    @SerialName("runtime_model_id") val runtimeModelId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("kind") val kind: ModelVariantKind = ModelVariantKind.DIRECT,
    @SerialName("reasoning_profile") val reasoningProfile: ReasoningProfile? = null,
    @SerialName("tier_member_variant_ids") val tierMemberVariantIds: List<String> = emptyList(),
    @SerialName("parameter_overrides") val parameterOverrides: ParameterOverrides? = null,
    @SerialName("enabled") val enabled: Boolean = true
)

@Serializable
enum class CompressionPolicyTargetType {
    @SerialName("official_catalog_model")
    OFFICIAL_CATALOG_MODEL,

    @SerialName("provider_model_binding")
    PROVIDER_MODEL_BINDING,

    @SerialName("model_route_variant")
    MODEL_ROUTE_VARIANT
}

@Serializable
data class ModelCompressionPolicyAssignment(
    @SerialName("target_type") val targetType: CompressionPolicyTargetType,
    @SerialName("target_id") val targetId: String,
    @SerialName("policy") val policy: ModelCompressionPolicy
)
