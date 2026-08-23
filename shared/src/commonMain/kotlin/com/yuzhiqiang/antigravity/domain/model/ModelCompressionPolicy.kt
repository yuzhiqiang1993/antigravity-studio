package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 与 agy-byok 保持一致的 Checkpointer 策略。
 *
 * 宿主会把这组字段作为模型目录中的实验配置读取。Studio 旧版本只保存了
 * 三个 Token 字段，因此保留只读别名，并在 ConfigStore 解码时完成旧字段迁移。
 */
@Serializable
data class ModelCompressionPolicy(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("checkpoint_model")
    val checkpointModel: String = DEFAULT_CHECKPOINT_MODEL,
    @SerialName("strategy")
    val strategy: String = DEFAULT_STRATEGY,
    @SerialName("max_overhead_ratio")
    val maxOverheadRatio: String = "0.30",
    @SerialName("moving_window_size")
    val movingWindowSize: String = "1",
    @SerialName("use_last_planner_model")
    val useLastPlannerModel: Boolean = false,
    @SerialName("is_sync")
    val isSync: Boolean = false,
    @SerialName("max_user_requests")
    val maxUserRequests: Int = 10,
    @SerialName("include_last_user_message")
    val includeLastUserMessage: Boolean = false,
    @SerialName("include_conversation_log")
    val includeConversationLog: Boolean = true,
    @SerialName("include_running_task_snapshots")
    val includeRunningTaskSnapshots: Boolean = true,
    @SerialName("include_subagent_snapshots")
    val includeSubagentSnapshots: Boolean = true,
    @SerialName("include_artifact_snapshots")
    val includeArtifactSnapshots: Boolean = true,
    @SerialName("retry_config")
    val retryConfig: CustomModelCheckpointRetryConfig = CustomModelCheckpointRetryConfig(),
    @SerialName("token_threshold")
    val tokenThreshold: Long = 50_000L,
    @SerialName("max_token_limit")
    val maxTokenLimit: Long = 128_000L,
    @SerialName("max_output_tokens")
    val maxOutputTokens: Long = 16_384L
) {
    /** Studio 旧 UI 的兼容别名；新配置统一使用 byok 字段。 */
    val triggerThresholdTokens: Long
        get() = tokenThreshold

    /** Studio 旧 UI 的兼容别名；新配置统一使用 byok 字段。 */
    val maxCheckpointTokens: Long
        get() = maxTokenLimit

    /** Studio 旧 UI 的兼容别名；新配置统一使用 byok 字段。 */
    val reserveOutputTokens: Long
        get() = maxOutputTokens

    /** 旧 Studio 的 target_context_tokens 不是 byok 契约字段，仅保留读取语义。 */
    val targetContextTokens: Long
        get() = tokenThreshold

    fun resolveEffective(
        capacity: Long? = null,
        outputTokenLimit: Long? = null
    ): ModelCompressionPolicy? {
        val effectiveCapacity = capacity ?: maxTokenLimit
        val effectiveOutput = outputTokenLimit ?: maxOutputTokens
        if (effectiveCapacity < 2L || effectiveOutput <= 0L) return null

        val resolvedLimit = maxTokenLimit.coerceAtMost(effectiveCapacity)
        if (resolvedLimit < 2L) return null
        val resolvedOutput = maxOutputTokens
            .coerceAtMost(effectiveOutput)
            .coerceAtMost(resolvedLimit - 1L)
        if (resolvedOutput <= 0L) return null
        val resolvedThreshold = tokenThreshold
            .coerceAtMost(resolvedLimit - resolvedOutput)
        if (resolvedThreshold <= 0L) return null

        return copy(
            tokenThreshold = resolvedThreshold,
            maxTokenLimit = resolvedLimit,
            maxOutputTokens = resolvedOutput
        )
    }

    /** 与 byok 的领域校验保持同一组关键边界。 */
    fun validate(scope: String = "compression_policy") {
        require(checkpointModel.matches(Regex("MODEL_PLACEHOLDER_M\\d+"))) {
            "$scope checkpoint_model 必须匹配 MODEL_PLACEHOLDER_M<number>"
        }
        require(strategy.isNotBlank()) { "$scope strategy 不能为空" }
        require(maxOverheadRatio.toDoubleOrNull()?.isFinite() == true && maxOverheadRatio.toDouble() >= 0.0) {
            "$scope max_overhead_ratio 必须是非负有限数字"
        }
        require(movingWindowSize.toDoubleOrNull()?.isFinite() == true && movingWindowSize.toDouble() >= 0.0) {
            "$scope moving_window_size 必须是非负有限数字"
        }
        require(tokenThreshold > 0L && maxTokenLimit > 0L && maxOutputTokens > 0L) {
            "$scope Token 上限必须大于 0"
        }
        require(
            tokenThreshold <= 4_294_967_295L &&
                    maxTokenLimit <= 4_294_967_295L &&
                    maxOutputTokens <= 4_294_967_295L
        ) { "$scope Token 上限超出 byok 支持范围" }
        require(tokenThreshold < maxTokenLimit) {
            "$scope token_threshold 必须小于 max_token_limit"
        }
        require(maxOutputTokens < maxTokenLimit) {
            "$scope max_output_tokens 必须小于 max_token_limit"
        }
        require(tokenThreshold <= maxTokenLimit - maxOutputTokens) {
            "$scope token_threshold 与 max_output_tokens 之和不能超过 max_token_limit"
        }
        require(maxUserRequests >= 0) { "$scope max_user_requests 不能为负数" }
        require(retryConfig.maxRetries >= 0) { "$scope retry_config.max_retries 不能为负数" }
        require(retryConfig.initialSleepDurationMs >= 0) {
            "$scope retry_config.initial_sleep_duration_ms 不能为负数"
        }
        require(retryConfig.exponentialMultiplier >= 1) {
            "$scope retry_config.exponential_multiplier 必须大于等于 1"
        }
    }

    companion object {
        const val DEFAULT_CHECKPOINT_MODEL = "MODEL_PLACEHOLDER_M50"
        const val DEFAULT_STRATEGY = "CHECKPOINT_STRATEGY_UNSPECIFIED"

        fun preset128k() = ModelCompressionPolicy(
            tokenThreshold = 50_000L,
            maxTokenLimit = 128_000L,
            maxOutputTokens = 16_384L
        )

        fun preset200k() = ModelCompressionPolicy(
            tokenThreshold = 150_000L,
            maxTokenLimit = 200_000L,
            maxOutputTokens = 16_000L
        )

        fun preset300k() = ModelCompressionPolicy(
            tokenThreshold = 225_000L,
            maxTokenLimit = 300_000L,
            maxOutputTokens = 16_000L
        )

        fun preset400k() = ModelCompressionPolicy(
            tokenThreshold = 300_000L,
            maxTokenLimit = 400_000L,
            maxOutputTokens = 20_000L
        )

        fun preset500k() = ModelCompressionPolicy(
            tokenThreshold = 375_000L,
            maxTokenLimit = 500_000L,
            maxOutputTokens = 24_000L
        )

        fun preset600k() = ModelCompressionPolicy(
            tokenThreshold = 450_000L,
            maxTokenLimit = 600_000L,
            maxOutputTokens = 30_000L
        )

        fun preset700k() = ModelCompressionPolicy(
            tokenThreshold = 520_000L,
            maxTokenLimit = 700_000L,
            maxOutputTokens = 32_000L
        )

        fun preset256k() = ModelCompressionPolicy(
            tokenThreshold = 102_400L,
            maxTokenLimit = 153_600L,
            maxOutputTokens = 30_720L
        )

        fun preset372k() = ModelCompressionPolicy(
            tokenThreshold = 148_800L,
            maxTokenLimit = 223_200L,
            maxOutputTokens = 44_640L
        )

        fun preset1m() = ModelCompressionPolicy(
            tokenThreshold = 419_430L,
            maxTokenLimit = 629_145L,
            maxOutputTokens = 65_535L
        )
    }
}

@Serializable
data class CustomModelCheckpointRetryConfig(
    @SerialName("max_retries") val maxRetries: Int = 0,
    @SerialName("initial_sleep_duration_ms") val initialSleepDurationMs: Int = 1_000,
    @SerialName("exponential_multiplier") val exponentialMultiplier: Int = 2,
    @SerialName("include_error_feedback") val includeErrorFeedback: Boolean = false
)
