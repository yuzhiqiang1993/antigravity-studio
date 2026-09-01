package com.yuzhiqiang.antigravity.proxy.model

/**
 * 收口 Provider usage 的非负校验与总量守恒。
 * 上报总量大于已知分项时，差额保留为未归因 Token；不能静默伪装成任一计费维度。
 */
internal fun normalizedNeutralUsage(
    inputTokens: Long? = null,
    outputTokens: Long? = null,
    cacheReadTokens: Long? = null,
    cacheWriteTokens: Long? = null,
    reasoningTokens: Long? = null,
    unattributedTokens: Long? = null,
    reportedTotalTokens: Long? = null
): NeutralUsage? {
    val input = inputTokens.nonNegativeOrNull()
    val output = outputTokens.nonNegativeOrNull()
    val cacheRead = cacheReadTokens.nonNegativeOrNull()
    val cacheWrite = cacheWriteTokens.nonNegativeOrNull()
    val reasoning = reasoningTokens.nonNegativeOrNull()
    val explicitUnattributed = unattributedTokens.nonNegativeOrNull()
    val reportedTotal = reportedTotalTokens.nonNegativeOrNull()

    val hasKnownValue = listOf(
        input,
        output,
        cacheRead,
        cacheWrite,
        reasoning,
        explicitUnattributed,
        reportedTotal
    ).any { it != null }
    if (!hasKnownValue) return null

    val attributedTotal = saturatedSum(input, output, cacheRead, cacheWrite, reasoning)
    val knownTotal = saturatedSum(attributedTotal, explicitUnattributed)
    val reportedGap = reportedTotal
        ?.takeIf { it > knownTotal }
        ?.minus(knownTotal)
        ?: 0L
    val unattributed = saturatedSum(explicitUnattributed, reportedGap)
    val total = saturatedSum(attributedTotal, unattributed)

    return NeutralUsage(
        inputTokens = input,
        outputTokens = output,
        cacheReadTokens = cacheRead,
        cacheWriteTokens = cacheWrite,
        reasoningTokens = reasoning,
        unattributedTokens = unattributed.takeIf { it > 0L },
        totalTokens = total
    )
}

private fun Long?.nonNegativeOrNull(): Long? = this?.takeIf { it >= 0L }

private fun saturatedSum(vararg values: Long?): Long {
    var total = 0L
    for (value in values) {
        val safeValue = value ?: continue
        total = if (safeValue > Long.MAX_VALUE - total) Long.MAX_VALUE else total + safeValue
    }
    return total
}
