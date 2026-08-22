package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
            OFF -> "Off (关闭推理)"
            LOW -> "Low (轻量推理)"
            MEDIUM -> "Medium (标准推理)"
            HIGH -> "High (深度推理)"
            X_HIGH -> "X-High (增强推理)"
            MAX -> "Max (极限推理)"
            ADAPTIVE -> "Adaptive (自适应推理)"
            AUTO -> "Auto (自动推理)"
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
    /** 兼容 agy 对象映射、Studio 历史数组及 null。 */
    @SerialName("levels") val levels: JsonElement? = null,
    /** Studio 历史字段，agy 以 levels/mappings 表达档位。 */
    @SerialName("default_level") val defaultLevel: ReasoningLevel? = null,
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
    @SerialName("reasoning") val reasoning: ReasoningCapability = ReasoningCapability(),
    /** Studio 旧配置兼容字段；新目录应使用 input_modalities。 */
    @SerialName("vision") val vision: Boolean = false
) {
    val supportsVision: Boolean
        get() = vision || ModelModality.IMAGE in inputModalities
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

/** Studio 旧调用方的类型别名；JSON 结构以 agy ModelTokenLimits 为准。 */
typealias TokenLimits = ModelTokenLimits

/** 允许 agy tokenizer 对象、Studio 历史字符串或 null 先以 JSON 保真承接。 */
typealias TokenizerConfig = JsonElement

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
    @SerialName("tokenizer") val tokenizer: TokenizerConfig? = null,
    /** 旧 Studio 配置兼容；agy 的稳定 host id 由 VirtualModel 提供。 */
    @SerialName("host_model_id") val hostModelId: String? = null,
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
    @SerialName("host_model_id") val hostModelId: String? = null,
    @SerialName("capabilities") val capabilities: ModelCapabilities = ModelCapabilities(),
    @SerialName("parameter_overrides") val parameterOverrides: ParameterOverrides? = null,
    @SerialName("default_reasoning_level") val defaultReasoningLevel: ReasoningLevel? = null,
    @SerialName("fallback_virtual_model_id") val fallbackVirtualModelId: String? = null,
    @SerialName("enabled") val enabled: Boolean = true
)
