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
data class UpstreamModel(
    @SerialName("id") val id: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("name") val name: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("upstream_model_id") val upstreamModelId: String,
    @SerialName("capabilities") val capabilities: ModelCapabilities = ModelCapabilities(),
    @SerialName("token_limits") val tokenLimits: ModelTokenLimits = ModelTokenLimits(),
    @SerialName("compression_policy") val compressionPolicy: ModelCompressionPolicy? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Long? = null,
    @SerialName("tokenizer") val tokenizer: JsonObject? = null,
    @SerialName("parameter_overrides") val parameterOverrides: ParameterOverrides = ParameterOverrides(),
    @SerialName("enabled") val enabled: Boolean = true
) {
    val effectiveName: String
        get() = displayName?.ifBlank { null } ?: name.ifBlank { null } ?: upstreamModelId

    /** 未知 token limit 保持 null，不再伪造 200K/65K。 */
    val effectiveContextWindow: Long?
        get() = tokenLimits.contextWindow ?: contextLength
}

@Serializable
data class VirtualModel(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("upstream_model_id") val upstreamModelId: String,
    @SerialName("host_model_id") val hostModelId: String,
    @SerialName("capabilities") val capabilities: ModelCapabilities = ModelCapabilities(),
    @SerialName("parameter_overrides") val parameterOverrides: ParameterOverrides? = null,
    @SerialName("default_reasoning_level") val defaultReasoningLevel: ReasoningLevel? = null,
    @SerialName("enabled") val enabled: Boolean = true
)
