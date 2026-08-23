package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

enum class ModelBrand(
    val brandName: String,
    val colors: AppTokens.Brand.Colors,
    val iconVector: ImageVector
) {
    GEMINI("Google DeepMind", AppTokens.Brand.gemini, Icons.Outlined.AutoAwesome),
    CLAUDE("Anthropic", AppTokens.Brand.claude, Icons.Outlined.Psychology),
    OPENAI("OpenAI", AppTokens.Brand.openAi, Icons.Outlined.DataObject),
    DEEPSEEK("DeepSeek", AppTokens.Brand.deepSeek, Icons.Outlined.Terminal),
    QWEN("Alibaba Cloud", AppTokens.Brand.qwen, Icons.Outlined.Cloud),
    CUSTOM("Custom Model", AppTokens.Brand.custom, Icons.Outlined.Dns);

    companion object {
        fun fromModelName(name: String): ModelBrand {
            val lower = name.lowercase()
            return when {
                lower.contains("gemini") -> GEMINI
                lower.contains("claude") || lower.contains("sonnet") ||
                        lower.contains("opus") || lower.contains("haiku") -> CLAUDE
                lower.contains("gpt") || lower.contains("o1") ||
                        lower.contains("o3") || lower.contains("chatgpt") -> OPENAI
                lower.contains("deepseek") -> DEEPSEEK
                lower.contains("qwen") -> QWEN
                else -> CUSTOM
            }
        }
    }
}

