package com.yuzhiqiang.antigravity.proxy.checkpoint

import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy

object Checkpointer {

    fun resolveEffectiveLimits(
        policy: ModelCompressionPolicy?,
        defaultContextLength: Long?
    ): ModelCompressionPolicy {
        val base = policy ?: ModelCompressionPolicy.preset200k()
        return base.resolveEffective(defaultContextLength).let { resolved ->
            resolved ?: base
        }
    }
}
