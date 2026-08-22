package com.yuzhiqiang.antigravity.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelCompressionPolicy(
    @SerialName("trigger_threshold_tokens")
    val triggerThresholdTokens: Long = 148_000L,
    @SerialName("target_context_tokens")
    val targetContextTokens: Long = 128_000L,
    @SerialName("max_checkpoint_tokens")
    val maxCheckpointTokens: Long = 200_000L,
    @SerialName("reserve_output_tokens")
    val reserveOutputTokens: Long = 16_000L,
    @SerialName("worker_model_id")
    val workerModelId: String? = null
) {
    companion object {
        fun preset128k() = ModelCompressionPolicy(
            triggerThresholdTokens = 98_000L,
            targetContextTokens = 84_000L,
            maxCheckpointTokens = 128_000L,
            reserveOutputTokens = 12_000L
        )

        fun preset200k() = ModelCompressionPolicy(
            triggerThresholdTokens = 152_000L,
            targetContextTokens = 130_000L,
            maxCheckpointTokens = 200_000L,
            reserveOutputTokens = 16_000L
        )

        fun preset256k() = ModelCompressionPolicy(
            triggerThresholdTokens = 196_000L,
            targetContextTokens = 168_000L,
            maxCheckpointTokens = 256_000L,
            reserveOutputTokens = 20_000L
        )

        fun preset372k() = ModelCompressionPolicy(
            triggerThresholdTokens = 286_000L,
            targetContextTokens = 244_000L,
            maxCheckpointTokens = 372_000L,
            reserveOutputTokens = 28_000L
        )

        fun preset1m() = ModelCompressionPolicy(
            triggerThresholdTokens = 768_000L,
            targetContextTokens = 655_000L,
            maxCheckpointTokens = 1_000_000L,
            reserveOutputTokens = 32_000L
        )
    }
}
