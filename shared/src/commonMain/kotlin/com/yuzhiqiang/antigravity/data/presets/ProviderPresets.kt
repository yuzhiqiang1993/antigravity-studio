package com.yuzhiqiang.antigravity.data.presets

import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol

enum class PresetCategory {
    ALL,
    AGGREGATOR,
    RECOMMENDED,
    OFFICIAL,
    LOCAL_CUSTOM
}

data class PresetProviderTemplate(
    val id: String,
    val name: String,
    val protocol: ProviderProtocol,
    val defaultBaseUrl: String,
    val description: String,
    val category: PresetCategory = PresetCategory.RECOMMENDED,
    val placeholderKey: String = "sk-...",
    val iconName: String = "model"
)

object ProviderPresets {
    val allPresets: List<PresetProviderTemplate> = listOf(
        PresetProviderTemplate(
            id = "custom_openai",
            name = "自定义",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "",
            category = PresetCategory.LOCAL_CUSTOM,
            description = "手动配置任意 OpenAI 兼容、Anthropic 或 Gemini 服务"
        ),
        PresetProviderTemplate(
            id = "cpa",
            name = "CPA",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "http://127.0.0.1:8317/v1",
            category = PresetCategory.AGGREGATOR,
            description = "本地 CPA 聚合网关"
        ),
        PresetProviderTemplate(
            id = "sub2api",
            name = "Sub2API",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "http://127.0.0.1:8080/v1",
            category = PresetCategory.AGGREGATOR,
            description = "本地 Sub2API 聚合网关"
        ),
        PresetProviderTemplate(
            id = "openrouter",
            name = "OpenRouter",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            category = PresetCategory.AGGREGATOR,
            description = "全球 AI 模型聚合网关，支持数百款模型"
        ),
        PresetProviderTemplate(
            id = "modelgate",
            name = "ModelGate",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://mg.aid.pub/v1",
            category = PresetCategory.AGGREGATOR,
            description = "ModelGate 聚合服务"
        ),
        PresetProviderTemplate(
            id = "anthropic",
            name = "Claude 官方",
            protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
            defaultBaseUrl = "https://api.anthropic.com",
            category = PresetCategory.OFFICIAL,
            description = "Anthropic 官方 Messages API"
        ),
        PresetProviderTemplate(
            id = "openai",
            name = "OpenAI 官方",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.openai.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "OpenAI 官方 Chat Completions API"
        ),
        PresetProviderTemplate(
            id = "gemini",
            name = "Gemini 官方",
            protocol = ProviderProtocol.GEMINI_GENERATE_CONTENT,
            defaultBaseUrl = "https://generativelanguage.googleapis.com",
            category = PresetCategory.OFFICIAL,
            description = "Google Gemini 原生 GenerateContent API"
        ),
        PresetProviderTemplate(
            id = "deepseek",
            name = "DeepSeek",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.deepseek.com",
            category = PresetCategory.RECOMMENDED,
            description = "DeepSeek 官方 OpenAI 兼容接口"
        ),
        PresetProviderTemplate(
            id = "ollama",
            name = "Ollama",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "http://127.0.0.1:11434/v1",
            category = PresetCategory.LOCAL_CUSTOM,
            description = "本地大模型运行框架",
            placeholderKey = "ollama"
        ),
        PresetProviderTemplate(
            id = "siliconflow",
            name = "硅基流动",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.siliconflow.cn/v1",
            category = PresetCategory.RECOMMENDED,
            description = "国内推理聚合加速平台"
        ),
        PresetProviderTemplate(
            id = "dashscope",
            name = "阿里云百炼",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            category = PresetCategory.RECOMMENDED,
            description = "阿里云百炼 OpenAI 兼容接口"
        ),
        PresetProviderTemplate(
            id = "moonshot",
            name = "Kimi",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.moonshot.cn/v1",
            category = PresetCategory.RECOMMENDED,
            description = "Moonshot AI 官方 API"
        ),
        PresetProviderTemplate(
            id = "zhipu",
            name = "智谱 AI",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
            category = PresetCategory.OFFICIAL,
            description = "智谱 AI 开放平台"
        ),
        PresetProviderTemplate(
            id = "minimax",
            name = "MiniMax",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.minimaxi.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "MiniMax 官方 API"
        ),
        PresetProviderTemplate(
            id = "hunyuan",
            name = "腾讯混元",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "腾讯混元 OpenAI 兼容接口"
        ),
        PresetProviderTemplate(
            id = "volcengine",
            name = "火山方舟",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            category = PresetCategory.OFFICIAL,
            description = "火山方舟模型推理服务"
        ),
        PresetProviderTemplate(
            id = "qianfan",
            name = "百度千帆",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://qianfan.baidubce.com/v2",
            category = PresetCategory.OFFICIAL,
            description = "百度千帆模型服务"
        ),
        PresetProviderTemplate(
            id = "baichuan",
            name = "百川智能",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.baichuan-ai.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "百川智能官方 API"
        ),
        PresetProviderTemplate(
            id = "yi",
            name = "零一万物",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.lingyiwanwu.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "零一万物 Yi 系列模型"
        ),
        PresetProviderTemplate(
            id = "xunfei",
            name = "讯飞星火",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://spark-api-open.xf-yun.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "讯飞星火 OpenAI 兼容接口"
        ),
        PresetProviderTemplate(
            id = "stepfun",
            name = "阶跃星辰",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.stepfun.com/v1",
            category = PresetCategory.OFFICIAL,
            description = "阶跃星辰官方 API"
        ),
        PresetProviderTemplate(
            id = "groq",
            name = "Groq",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            category = PresetCategory.AGGREGATOR,
            description = "Groq 高速推理 API"
        ),
        PresetProviderTemplate(
            id = "github",
            name = "GitHub Models",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://models.inference.ai.azure.com",
            category = PresetCategory.AGGREGATOR,
            description = "GitHub Models 推理接口"
        ),
        PresetProviderTemplate(
            id = "mistral",
            name = "Mistral",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.mistral.ai/v1",
            category = PresetCategory.OFFICIAL,
            description = "Mistral 官方 API"
        ),
        PresetProviderTemplate(
            id = "xai",
            name = "xAI",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.x.ai/v1",
            category = PresetCategory.OFFICIAL,
            description = "xAI Grok 官方 API"
        ),
        PresetProviderTemplate(
            id = "perplexity",
            name = "Perplexity",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.perplexity.ai",
            category = PresetCategory.OFFICIAL,
            description = "Perplexity 官方 API"
        ),
        PresetProviderTemplate(
            id = "together",
            name = "Together AI",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.together.xyz/v1",
            category = PresetCategory.AGGREGATOR,
            description = "Together AI 模型聚合服务"
        ),
        PresetProviderTemplate(
            id = "fireworks",
            name = "Fireworks AI",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
            category = PresetCategory.AGGREGATOR,
            description = "Fireworks AI 推理服务"
        ),
        PresetProviderTemplate(
            id = "cerebras",
            name = "Cerebras",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.cerebras.ai/v1",
            category = PresetCategory.AGGREGATOR,
            description = "Cerebras 高速推理 API"
        ),
        PresetProviderTemplate(
            id = "sambanova",
            name = "SambaNova",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.sambanova.ai/v1",
            category = PresetCategory.AGGREGATOR,
            description = "SambaNova 推理服务"
        ),
        PresetProviderTemplate(
            id = "deepinfra",
            name = "DeepInfra",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.deepinfra.com/v1/openai",
            category = PresetCategory.AGGREGATOR,
            description = "DeepInfra 模型推理服务"
        ),
        PresetProviderTemplate(
            id = "huggingface",
            name = "Hugging Face",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://router.huggingface.co/v1",
            category = PresetCategory.AGGREGATOR,
            description = "Hugging Face Inference Providers"
        ),
        PresetProviderTemplate(
            id = "novita",
            name = "Novita AI",
            protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultBaseUrl = "https://api.novita.ai/openai",
            category = PresetCategory.AGGREGATOR,
            description = "Novita AI 推理服务"
        )
    )
}
