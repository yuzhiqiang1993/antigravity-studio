package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.ui.graphics.Color

data class ModelBrandStyle(
    val badge: String,
    val container: Color,
    val contentColor: Color
)


fun getModelBrandStyle(modelId: String, modelName: String): ModelBrandStyle {
    val lower = "$modelId $modelName".lowercase()
    return when {
        lower.contains("gemini") -> ModelBrandStyle("G", Color(0xFFE8F0FE), Color(0xFF1967D2))
        lower.contains("claude") -> ModelBrandStyle("C", Color(0xFFFCE8E6), Color(0xFFC5221F))
        lower.contains("gpt") || lower.contains("openai") || lower.contains("o1") ||
                lower.contains("o3") || lower.contains("o4") || lower.contains("chatgpt") ->
            ModelBrandStyle("O", Color(0xFFE6F4EA), Color(0xFF137333))
        lower.contains("deepseek") -> ModelBrandStyle("D", Color(0xFFEEF2FF), Color(0xFF4F46E5))
        lower.contains("grok") || lower.contains("xai") -> ModelBrandStyle("X", Color(0xFFF1F3F4), Color(0xFF202124))
        lower.contains("qwen") || lower.contains("tongyi") -> ModelBrandStyle("Q", Color(0xFFF3E8FD), Color(0xFF7E22CE))
        lower.contains("llama") || lower.contains("meta") -> ModelBrandStyle("M", Color(0xFFE0F2FE), Color(0xFF0369A1))
        lower.contains("mistral") || lower.contains("codestral") -> ModelBrandStyle("M", Color(0xFFFFF7ED), Color(0xFFEA580C))
        lower.contains("glm") || lower.contains("zhipu") || lower.contains("chatglm") -> ModelBrandStyle("Z", Color(0xFFEFF6FF), Color(0xFF2563EB))
        lower.contains("kimi") || lower.contains("moonshot") -> ModelBrandStyle("K", Color(0xFFFDF2F8), Color(0xFFDB2777))
        lower.contains("minimax") -> ModelBrandStyle("M", Color(0xFFFEF2F2), Color(0xFFDC2626))
        lower.contains("yi-") || lower.contains("01-ai") -> ModelBrandStyle("Y", Color(0xFFF0FDF4), Color(0xFF16A34A))
        lower.contains("baichuan") -> ModelBrandStyle("B", Color(0xFFFFFBEB), Color(0xFFD97706))
        lower.contains("step") || lower.contains("stepfun") -> ModelBrandStyle("S", Color(0xFFF5F3FF), Color(0xFF7C3AED))
        else -> ModelBrandStyle(
            modelName.firstOrNull()?.uppercase() ?: "M",
            Color(0xFFF3F4F6),
            Color(0xFF4B5563)
        )
    }
}
