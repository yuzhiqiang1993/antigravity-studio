package com.yuzhiqiang.antigravity.proxy.checkpoint

import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy

object Checkpointer {

    fun resolveEffectiveLimits(
        policy: ModelCompressionPolicy?,
        defaultContextLength: Long?
    ): ModelCompressionPolicy {
        return policy ?: ModelCompressionPolicy.preset200k()
    }
}
