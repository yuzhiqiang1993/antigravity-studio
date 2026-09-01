package com.yuzhiqiang.antigravity.ui.utils

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.proxy.catalog.OfficialCatalogProbe

object ModelDisplayNameResolver {

    /**
     * 将 modelId 解析为易读的 displayName：
     * 1. 优先在 officialModels 中匹配（精确或 catalogModel + 档位）
     * 2. 其次在 config.modelRouteVariants / providerModelBindings 中匹配配置好的 displayName
     * 3. 兜底通过智能规则格式化（如去掉 custom- 前缀、识别常见模型名、推理档位格式化）
     */
    fun resolve(
        modelId: String,
        config: AppConfig? = null,
        officialModels: List<OfficialCatalogModel>? = null
    ): String {
        val trimmed = modelId.trim()
        if (trimmed.isBlank()) return ""

        // 1. 查找官方模型列表
        val effectiveOfficial = officialModels?.takeIf { it.isNotEmpty() }
            ?: OfficialCatalogProbe.lastParsedModels
        val exactOfficial = effectiveOfficial.firstOrNull { model ->
            model.catalogModelId.equals(trimmed, ignoreCase = true) ||
                    model.runtimeModelId?.equals(trimmed, ignoreCase = true) == true ||
                    model.providerModelId?.equals(trimmed, ignoreCase = true) == true
        }
        if (exactOfficial != null && exactOfficial.displayName.isNotBlank()) {
            return exactOfficial.displayName
        }

        // 2. 查找配置中的路由变体或 Provider 模型 Binding
        if (config != null) {
            val variant = config.modelRouteVariants.firstOrNull { candidate ->
                candidate.variantId.equals(trimmed, ignoreCase = true) ||
                        candidate.catalogModelId.equals(trimmed, ignoreCase = true) ||
                        candidate.runtimeModelId.equals(trimmed, ignoreCase = true)
            }
            if (variant != null) {
                return variant.displayName.ifBlank {
                    config.providerModelBindings
                        .firstOrNull { binding -> binding.bindingId == variant.bindingId }
                        ?.effectiveName
                        ?: trimmed
                }
            }

            val binding = config.providerModelBindings.firstOrNull { candidate ->
                candidate.bindingId.equals(trimmed, ignoreCase = true) ||
                        candidate.providerModelId.equals(trimmed, ignoreCase = true)
            }
            if (binding != null) {
                return binding.effectiveName
            }
        }

        // 3. 官方模型基础 ID + 推理档位尝试
        val (baseId, reasoningLevel) = extractReasoningLevel(trimmed)
        if (reasoningLevel != null) {
            val baseOfficial = effectiveOfficial.firstOrNull { model ->
                model.catalogModelId.equals(baseId, ignoreCase = true)
            }
            if (baseOfficial != null && baseOfficial.displayName.isNotBlank()) {
                val cleanBase = ModelIdentity.stripDisplayLevelSuffix(baseOfficial.displayName)
                return "$cleanBase (${reasoningLevel.label})"
            }
        }

        // 4. 智能人类可读格式化兜底
        return formatHumanReadableModelName(trimmed)
    }

    private fun extractReasoningLevel(modelId: String): Pair<String, ReasoningLevel?> {
        val lower = modelId.lowercase()
        val suffixMap = listOf(
            "-x-high" to ReasoningLevel.X_HIGH,
            "-x_high" to ReasoningLevel.X_HIGH,
            "-high" to ReasoningLevel.HIGH,
            "-medium" to ReasoningLevel.MEDIUM,
            "-low" to ReasoningLevel.LOW,
            "-max" to ReasoningLevel.MAX,
            "-adaptive" to ReasoningLevel.ADAPTIVE,
            "-auto" to ReasoningLevel.AUTO,
            "-off" to ReasoningLevel.OFF
        )
        for ((suffix, level) in suffixMap) {
            if (lower.endsWith(suffix)) {
                return modelId.substring(0, modelId.length - suffix.length) to level
            }
        }
        return modelId to null
    }

    internal fun formatHumanReadableModelName(modelId: String): String {
        var clean = modelId.trim()
        val (stripped, level) = extractReasoningLevel(clean)
        clean = stripped

        clean = clean.removePrefix("custom-byok-").removePrefix("custom-")

        val parts = clean.split('-', '_').filter { it.isNotBlank() }
        val formattedParts = parts.map { part ->
            when {
                part.equals("gpt", ignoreCase = true) -> "GPT"
                part.equals("claude", ignoreCase = true) -> "Claude"
                part.equals("gemini", ignoreCase = true) -> "Gemini"
                part.equals("qwen", ignoreCase = true) -> "Qwen"
                part.equals("deepseek", ignoreCase = true) -> "DeepSeek"
                part.equals("flash", ignoreCase = true) -> "Flash"
                part.equals("pro", ignoreCase = true) -> "Pro"
                part.equals("lite", ignoreCase = true) -> "Lite"
                part.equals("ultra", ignoreCase = true) -> "Ultra"
                part.equals("mini", ignoreCase = true) -> "Mini"
                part.equals("turbo", ignoreCase = true) -> "Turbo"
                part.matches(Regex("""^\d+(\.\d+)?$""")) -> part
                else -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        // 合并连续数字为版本号（如 ["GPT", "5", "6", "Sol"] -> "GPT 5.6 Sol"）
        val merged = mutableListOf<String>()
        var i = 0
        while (i < formattedParts.size) {
            val curr = formattedParts[i]
            if (curr.all { it.isDigit() } && i + 1 < formattedParts.size && formattedParts[i + 1].all { it.isDigit() }) {
                merged.add("$curr.${formattedParts[i + 1]}")
                i += 2
            } else {
                merged.add(curr)
                i++
            }
        }

        val baseFormatted = merged.joinToString(" ")
        return if (level != null) {
            "$baseFormatted (${level.label})"
        } else {
            baseFormatted
        }
    }
}
