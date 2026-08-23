package com.yuzhiqiang.antigravity.ui.dialogs.provider

import com.yuzhiqiang.antigravity.data.presets.ProviderPresets
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol

val INPUT_TOKEN_LIMIT_OPTIONS = listOf(
    131_072L to "128K",
    200_000L to "200K",
    262_144L to "256K",
    380_928L to "372K",
    524_288L to "512K",
    1_048_576L to "1M",
    2_097_152L to "2M"
)

val OUTPUT_TOKEN_LIMIT_OPTIONS = listOf(
    2_048L to "2K",
    4_096L to "4K",
    8_192L to "8K",
    16_384L to "16K",
    32_768L to "32K",
    65_536L to "64K",
    131_072L to "128K"
)

fun formatTokenDisplay(limit: Long?): String {
    if (limit == null || limit <= 0L) return "未设置"
    INPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.let { return it.second.substringBefore(" ") }
    OUTPUT_TOKEN_LIMIT_OPTIONS.find { it.first == limit }?.let { return it.second.substringBefore(" ") }

    return when {
        limit >= 1_048_576L && limit % 1_048_576L == 0L -> "${limit / 1_048_576L}M"
        limit >= 1_000_000L && limit % 1_000_000L == 0L -> "${limit / 1_000_000L}M"
        limit >= 1_000_000L -> "${((limit / 100_000.0).toInt()) / 10.0}M"
        limit >= 1024L && limit % 1024L == 0L -> "${limit / 1024L}K"
        limit >= 1000L && limit % 1000L == 0L -> "${limit / 1000L}K"
        limit >= 1000L -> "${limit / 1000L}K"
        else -> limit.toString()
    }
}

fun parseCustomTokenInput(text: String): Long? {
    val clean = text.trim().lowercase().replace(",", "").replace("_", "")
    if (clean.isBlank()) return null
    return try {
        when {
            clean.endsWith("m") -> {
                val num = clean.removeSuffix("m").trim().toDouble()
                (num * 1_048_576L).toLong()
            }
            clean.endsWith("k") -> {
                val num = clean.removeSuffix("k").trim().toDouble()
                (num * 1024L).toLong()
            }
            else -> clean.toLong()
        }
    } catch (_: NumberFormatException) {
        null
    }
}

fun suggestedEndpoints(baseUrl: String, protocol: ProviderProtocol): Pair<String, String> {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isBlank()) return "" to ""

    return if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
        val apiBase = if (base.endsWith("/v1beta")) base else "$base/v1beta"
        "$apiBase/models" to "$apiBase/models/{model}:generateContent"
    } else {
        val apiBase = if (base.endsWith("/v1")) base else "$base/v1"
        val generatePath = when (protocol) {
            ProviderProtocol.ANTHROPIC_MESSAGES -> "/messages"
            ProviderProtocol.OPENAI_RESPONSES -> "/responses"
            else -> "/chat/completions"
        }
        "$apiBase/models" to "$apiBase$generatePath"
    }
}

fun detectPresetId(baseUrl: String): String? {
    val host = baseUrl.trim()
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .lowercase()
    if (host.isBlank()) return null
    return ProviderPresets.allPresets.firstOrNull { preset ->
        val presetHost = preset.defaultBaseUrl
            .substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .lowercase()
        presetHost.isNotBlank() && presetHost == host
    }?.id
}

