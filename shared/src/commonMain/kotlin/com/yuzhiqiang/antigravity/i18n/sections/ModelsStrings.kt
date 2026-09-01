package com.yuzhiqiang.antigravity.i18n.sections

interface ModelsStrings {
    val modelsTitle: String
    val modelsSubtitle: String
    val modelsOfficialTab: String
    val modelsCustomTab: String
    val modelsOfficialTitle: String
    val modelsOfficialSubtitle: String
    val modelsCustomTitle: String
    val modelsAddProvider: String
    val modelsEditProvider: String
    val modelsDeleteProvider: String
    val modelsTestConnection: String
    val modelsFetchModels: String
    val modelsFetchingModels: String
    val modelsNoProviders: String
    val modelsCompressionPolicy: String
    val modelsReasoningConfig: String
    val modelsCollapse: String
    val modelsExpand: String
    val modelsContext: String
    val modelsVision: String
    val modelsTools: String
    val modelsReasoning: String
    val modelsNoModels: String
    val modelsTesting: String
    val modelsConnectionOk: String
    val modelsConnectionFailed: String
    val modelsRefreshOfficial: String
    val modelsFetchingOfficial: String
    fun modelsOfficialSyncFailed(error: String): String
    val modelsOfficialSyncing: String
    val modelsOfficialSynced: String
    val modelsOfficialWaitingSync: String
    val modelsRawJson: String
    val modelsModifiedJson: String
    val modelsNoOfficialDetected: String
    val modelsNoOfficialHint: String
    val modelsNoAccountTitle: String
    val modelsNoAccountHint: String
    val modelsGoToAccounts: String
    val modelsCopiedProviderUrl: String
    val modelsCopiedJson: String
    fun modelsPassedCount(passed: Int, total: Int): String
    fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int): String
    fun modelsRetryFailed(count: Int): String
    val modelsBatchTest: String
    val modelsEditConfig: String
    val modelsNoModelsHint: String
    val modelsDeleteProviderConfirmTitle: String
    fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int): String
    val modelsDeleteModelConfirmTitle: String
    fun modelsDeleteModelConfirmMessage(modelName: String): String
    val modelsRawJsonTitle: String
    val modelsModifiedJsonTitle: String
    val modelsJsonData: String
    fun modelsPolicyCapacityWithPrep(limit: String, prep: String): String
    val modelsOfficialDefault: String
    val modelsVisionDesc: String
    val modelsToolsDesc: String
    val modelsSpecsDesc: String
    fun modelsTestSuccess(latency: String): String
    val modelsTestFailed: String
    val modelsEnabledDesc: String
    val modelsDisabledDesc: String
    val modelsEditModel: String
    val modelsDeleteModel: String
    val modelsReasoningLevelLabel: String
    val modelsCompressionPolicyLabel: String
    val modelsEditPolicy: String
    val modelsCopyJson: String
    val modelsNotSet: String

    // Models Presentation Notices & Exceptions
    fun modelsProviderSaved(name: String): String
    fun modelsProviderSaveFailed(error: String): String
    fun modelsProviderDeleted(name: String): String
    fun modelsProviderDeleteFailed(error: String): String
    fun modelsModelDeleted(name: String): String
    fun modelsModelDeleteFailed(error: String): String
    fun modelsModelUpdated(name: String): String
    fun modelsModelUpdateFailed(error: String): String
    fun modelsModelTestSuccess(modelName: String, latencyMs: Long): String
    fun modelsModelTestFailed(modelName: String, error: String): String
    fun modelsBatchTestSuccess(passed: Int, total: Int): String
    fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int): String
    val modelsProviderNotFound: String
    val providerPresetCustomName: String
    val providerPresetCustomDesc: String

    // Model Capability & Specs Dialogs
    val modelReasoningTitle: String
    val modelReasoningDesc: String
    fun modelReasoningLevel(level: String): String
    val modelReasoningHighDesc: String
    val modelReasoningMediumDesc: String
    val modelReasoningLowDesc: String
    val modelReasoningAdaptiveDesc: String
    val modelVisionTitle: String
    val modelVisionDesc: String
    val modelVisionImageTitle: String
    val modelVisionImageDesc: String
    val modelVisionDocTitle: String
    val modelVisionDocDesc: String
    val modelToolsFunctionTitle: String
    val modelToolsFunctionDesc: String
    val modelSpecsTitle: String
    val modelSpecsId: String
    val modelSpecsContextWindow: String
    val modelSpecsDynamicConfig: String
    val modelSpecsMaxOutput: String
    val modelSpecsDefaultLimit: String
    val modelSpecsRoles: String

    // Policy Editor Dialog
    val policyPresetDefault: String
    val policyPresetCustom: String
    val policyRecommended: String
    val policyDefaultDesc: String
    val policyCustomDesc: String
    val policyPresetDesc: String
    val policyCompressorModel: String
    val policyFollowCurrent: String
    val policyOfficialDefault: String
    val policyCheckpoint: String
    val policyContextLimit: String
    val policyOutputReserve: String
    val policyByPercentage: String
    val policyExactTokens: String
    val policyQuickPreset: String
    val policyDistribution: String
    val policyLegendNormal: String
    val policyLegendArchive: String
    val policyLegendUnused: String
    fun policyModelContext(context: String): String
    val policyLimitMustPositive: String
    val policyThresholdMustPositive: String
    val policyReserveMustPositive: String
    fun policyLimitExceedsContext(limit: String, context: String): String
    fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String): String
    fun policyThresholdExceedsLimit(threshold: String, limit: String): String
    fun policySumExceedsLimit(sum: String, limit: String): String
    val policyFormulaHint: String
    val policyFormulaHintDesc: String

    // Reasoning Config Dialog
    val reasoningDialogTitle: String
    val reasoningEnableTitle: String
    val reasoningEnableSubtitle: String
    val reasoningAvailableLevels: String
    val reasoningCustomValue: String
    val reasoningOptional: String
    val reasoningCustomValueDesc: String
    val reasoningDefaultBudget: String
    val reasoningDynamicBudgetPlaceholder: String
    val reasoningMinBudgetTitle: String
    fun reasoningExamplePlaceholder(example: String): String
    val reasoningBudget: String
    val reasoningMinBudget: String
    fun reasoningMustBeInteger(label: String): String
    val reasoningGeminiBudgetValidation: String
    val reasoningMinBudgetMustPositive: String
    val reasoningMinBudgetExceedsBudget: String
    val reasoningOnlyGeminiSupportsBudget: String
    val reasoningCustomValueInvalid: String
    fun reasoningLevelInvalid(level: String): String
    val reasoningSelectAtLeastOne: String
}

object ModelsStringsZh : ModelsStrings {
    override val modelsTitle = "模型管理"
    override val modelsSubtitle = "统一管理 Google 官方模型与第三方模型服务，灵活配置上下文压缩与深度思考"
    override val modelsOfficialTab = "官方模型"
    override val modelsCustomTab = "自定义服务商"
    override val modelsOfficialTitle = "官方原生模型"
    override val modelsOfficialSubtitle = "管理 Antigravity 官方内置模型，支持按需隐藏或禁用"
    override val modelsCustomTitle = "自定义模型服务商"
    override val modelsAddProvider = "添加服务商"
    override val modelsEditProvider = "编辑服务商"
    override val modelsDeleteProvider = "删除服务商"
    override val modelsTestConnection = "测试连接"
    override val modelsFetchModels = "获取模型列表"
    override val modelsFetchingModels = "获取中…"
    override val modelsNoProviders = "暂无配置的模型服务商，点击右上角添加"
    override val modelsCompressionPolicy = "上下文策略"
    override val modelsReasoningConfig = "思考与推理"
    override val modelsCollapse = "收起"
    override val modelsExpand = "展开"
    override val modelsContext = "上下文"
    override val modelsVision = "多模态"
    override val modelsTools = "工具调用"
    override val modelsReasoning = "深度思考"
    override val modelsNoModels = "暂无模型"
    override val modelsTesting = "测试中…"
    override val modelsConnectionOk = "连通正常"
    override val modelsConnectionFailed = "连接失败"
    override val modelsRefreshOfficial = "刷新官方模型"
    override val modelsFetchingOfficial = "正在请求 Google 官方服务同步可用模型…"
    override fun modelsOfficialSyncFailed(error: String) = "官方模型同步失败：$error"
    override val modelsOfficialSyncing = "正在同步官方模型数据..."
    override val modelsOfficialSynced = "官方模型数据已同步"
    override val modelsOfficialWaitingSync = "等待同步官方模型数据"
    override val modelsRawJson = "原始 JSON"
    override val modelsModifiedJson = "修改后 JSON"
    override val modelsNoOfficialDetected = "当前未检测到官方模型"
    override val modelsNoOfficialHint = "请先在「账号配额」页登录或导入有效账号，随后点击「刷新」"
    override val modelsNoAccountTitle = "暂未登录或导入账号"
    override val modelsNoAccountHint = "首次使用请先登录或导入账号，系统将自动基于账号同步官方模型列表与配额。"
    override val modelsGoToAccounts = "导入 / 登录账号"
    override val modelsCopiedProviderUrl = "已复制服务地址"
    override val modelsCopiedJson = "已复制 JSON 数据"
    override fun modelsPassedCount(passed: Int, total: Int) = "$passed/$total 项通过"
    override fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int) = "$passed/$total 项通过 ($failed 项失败)"
    override fun modelsRetryFailed(count: Int) = "重试失败项 ($count)"
    override val modelsBatchTest = "批量测试"
    override val modelsEditConfig = "编辑配置"
    override val modelsNoModelsHint = "该服务商尚未添加模型，点击「编辑配置」添加或拉取"
    override val modelsDeleteProviderConfirmTitle = "删除服务商"
    override fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int) =
        "确定要删除服务商「$providerName」吗？该服务商下的 $modelCount 个模型配置将被一并删除。"

    override val modelsDeleteModelConfirmTitle = "删除模型"
    override fun modelsDeleteModelConfirmMessage(modelName: String) = "确定要删除模型「$modelName」吗？"
    override val modelsRawJsonTitle = "官方模型原始 JSON 数据"
    override val modelsModifiedJsonTitle = "官方模型下发给 IDE 的 JSON 数据"
    override val modelsJsonData = "JSON 数据"
    override fun modelsPolicyCapacityWithPrep(limit: String, prep: String) = "$limit 容量 ($prep 预备)"
    override val modelsOfficialDefault = "官方默认"
    override val modelsVisionDesc = "多模态能力 (Multimodal)"
    override val modelsToolsDesc = "原生工具调用 (Tool Call)"
    override val modelsSpecsDesc = "查看模型规格与参数"
    override fun modelsTestSuccess(latency: String) = "测试成功 ($latency)"
    override val modelsTestFailed = "测试失败"
    override val modelsEnabledDesc = "已启用（点击禁用）"
    override val modelsDisabledDesc = "已禁用（点击启用）"
    override val modelsEditModel = "编辑模型配置"
    override val modelsDeleteModel = "删除此模型"
    override val modelsReasoningLevelLabel = "思考/推理等级"
    override val modelsCompressionPolicyLabel = "上下文策略"
    override val modelsEditPolicy = "编辑策略"
    override val modelsCopyJson = "复制 JSON"
    override val modelsNotSet = "未设置"

    override fun modelsProviderSaved(name: String) = "已保存服务商「$name」"
    override fun modelsProviderSaveFailed(error: String) = "保存服务商失败：$error"
    override fun modelsProviderDeleted(name: String) = "已删除服务商「$name」"
    override fun modelsProviderDeleteFailed(error: String) = "删除服务商失败：$error"
    override fun modelsModelDeleted(name: String) = "已删除模型「$name」"
    override fun modelsModelDeleteFailed(error: String) = "删除模型失败：$error"
    override fun modelsModelUpdated(name: String) = "已更新模型「$name」配置"
    override fun modelsModelUpdateFailed(error: String) = "更新模型失败：$error"
    override fun modelsModelTestSuccess(modelName: String, latencyMs: Long) = "$modelName 测试成功 (${latencyMs}ms)"
    override fun modelsModelTestFailed(modelName: String, error: String) = "$modelName 测试失败: $error"
    override fun modelsBatchTestSuccess(passed: Int, total: Int) = "服务商测试完成：$passed/$total 项测试通过"
    override fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int) =
        "服务商测试完成：$passed/$total 项通过，${failed} 项失败"

    override val modelsProviderNotFound = "模型关联的服务商不存在"
    override val providerPresetCustomName = "自定义"
    override val providerPresetCustomDesc = "手动配置兼容 OpenAI、Anthropic 或 Google Gemini 协议的 API 服务"

    override val modelReasoningTitle = "深度思考与推理能力"
    override val modelReasoningDesc =
        "该模型支持深度思考/推理模式。在与 IDE 对话协同开发时，模型可开启思考链，深入分析复杂逻辑与架构："

    override fun modelReasoningLevel(level: String) = "档位: $level"
    override val modelReasoningHighDesc = "高预算思考 (适合极度复杂的算法与重构方案)"
    override val modelReasoningMediumDesc = "标准思考 (平衡推理深度与响应延迟)"
    override val modelReasoningLowDesc = "轻量思考 (快速给出思考结论)"
    override val modelReasoningAdaptiveDesc = "模型原生自适应深度思考"
    override val modelVisionTitle = "多模态输入支持"
    override val modelVisionDesc = "多模态能力允许模型直接理解视觉截图、设计图纸、架构图与代码引用："
    override val modelVisionImageTitle = "图像解析 (Vision)"
    override val modelVisionImageDesc = "支持上传 PNG / JPEG / WEBP 设计图、UI 报错截图进行直接分析"
    override val modelVisionDocTitle = "文档理解 (Document)"
    override val modelVisionDocDesc = "支持原生阅读 PDF / 文本规范文档并提取代码上下文"
    override val modelToolsFunctionTitle = "工具调用 (Function Calling)"
    override val modelToolsFunctionDesc = "支持 IDE 工具自动化执行与终端命令联动"
    override val modelSpecsTitle = "模型规格与元数据"
    override val modelSpecsId = "模型标识 (ID)"
    override val modelSpecsContextWindow = "上下文总窗口"
    override val modelSpecsDynamicConfig = "官方动态配置"
    override val modelSpecsMaxOutput = "单次最大输出"
    override val modelSpecsDefaultLimit = "官方默认限制"
    override val modelSpecsRoles = "分配角色"

    override val policyPresetDefault = "官方默认"
    override val policyPresetCustom = "自定义"
    override val policyRecommended = "推荐"
    override val policyDefaultDesc = "保持官方默认设置，遵循模型原生上下文与压缩策略。"
    override val policyCustomDesc = "可点击百分比快速设定，也可手动输入具体 Token 数值进行微调。"
    override val policyPresetDesc = "选择会话上下文容量：可直接使用预设值，或切换为自定义策略进行调整。"
    override val policyCompressorModel = "负责压缩的执行模型"
    override val policyFollowCurrent = "跟随当前模型"
    override val policyOfficialDefault = "官方默认"
    override val policyCheckpoint = "自动压缩点"
    override val policyContextLimit = "会话上下文容量"
    override val policyOutputReserve = "输出预留"
    override val policyByPercentage = "按百分比"
    override val policyExactTokens = "精准 Token"
    override val policyQuickPreset = "快捷预设"
    override val policyDistribution = "上下文容量分布"
    override val policyLegendNormal = "正常对话历史"
    override val policyLegendArchive = "自动压缩触发区"
    override val policyLegendUnused = "剩余未用容量"
    override fun policyModelContext(context: String) = "模型上下文 · $context"
    override val policyLimitMustPositive = "会话上下文容量必须大于 0"
    override val policyThresholdMustPositive = "自动压缩点必须大于 0"
    override val policyReserveMustPositive = "输出预留必须大于 0"
    override fun policyLimitExceedsContext(limit: String, context: String) =
        "会话上下文容量 ($limit) 不能超过模型上下文 ($context)"

    override fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String) =
        "会话容量 ($limit) 超过了最大安全上限 ($safeLimit) [计算公式: 模型上下文 $context - 输出预留 $reserve]"

    override fun policyThresholdExceedsLimit(threshold: String, limit: String) =
        "自动压缩点 ($threshold) 必须小于会话上下文容量 ($limit)"

    override fun policySumExceedsLimit(sum: String, limit: String) =
        "自动压缩点与输出预留之和 ($sum) 超过了会话上下文容量 ($limit)"

    override val policyFormulaHint =
        "约束公式：触发压缩上限 (MaxTokenLimit) ≤ 模型上下文 (ContextWindow) - 最大输出预留 (OutputLimit)"
    override val policyFormulaHintDesc = "客户端要求输入上下文必须为模型生成预留足够空间，超出上限会导致对话异常中断。"

    override val reasoningDialogTitle = "配置深度思考"
    override val reasoningEnableTitle = "开启深度思考与推理 (Reasoning)"
    override val reasoningEnableSubtitle = "为当前模型配置推理档位与思考预算"
    override val reasoningAvailableLevels = "可用推理档位"
    override val reasoningCustomValue = "自定义推理参数 (Custom Reasoning)"
    override val reasoningOptional = "选填"
    override val reasoningCustomValueDesc = "如需使用服务商特有的推理档位参数，可在此手动输入覆盖"
    override val reasoningDefaultBudget = "默认思考预算 (选填)"
    override val reasoningDynamicBudgetPlaceholder = "-1 表示动态预算"
    override val reasoningMinBudgetTitle = "最小思考预算 (选填)"
    override fun reasoningExamplePlaceholder(example: String) = "例如 $example"
    override val reasoningBudget = "思考预算"
    override val reasoningMinBudget = "最小思考预算"
    override fun reasoningMustBeInteger(label: String) = "${label}必须是整数"
    override val reasoningGeminiBudgetValidation = "Gemini 思考预算只能为 -1、0 或正整数"
    override val reasoningMinBudgetMustPositive = "最小思考预算必须大于 0"
    override val reasoningMinBudgetExceedsBudget = "最小思考预算不能大于思考预算"
    override val reasoningOnlyGeminiSupportsBudget = "仅 Gemini 协议支持模型级思考预算"
    override val reasoningCustomValueInvalid = "自定义推理参数不符合当前协议或输出上限约束"
    override fun reasoningLevelInvalid(level: String) = "推理档位 $level 不符合当前协议或输出上限约束"
    override val reasoningSelectAtLeastOne = "请至少选择一个推理档位，或填写思考预算"
}

object ModelsStringsEn : ModelsStrings {
    override val modelsTitle = "Models"
    override val modelsSubtitle =
        "Orchestrate Google official models & custom BYOK upstream providers with context compression and reasoning budgets"
    override val modelsOfficialTab = "Official"
    override val modelsCustomTab = "Custom Services"
    override val modelsOfficialTitle = "Official Native Models"
    override val modelsOfficialSubtitle = "Manage built-in Antigravity models, disable unwanted models"
    override val modelsCustomTitle = "Custom Model Providers"
    override val modelsAddProvider = "Add Upstream Provider"
    override val modelsEditProvider = "Edit Provider"
    override val modelsDeleteProvider = "Delete Provider"
    override val modelsTestConnection = "Test Connection"
    override val modelsFetchModels = "Fetch Models"
    override val modelsFetchingModels = "Fetching…"
    override val modelsNoProviders = "No providers configured. Click top right to add."
    override val modelsCompressionPolicy = "Compression Policy"
    override val modelsReasoningConfig = "Reasoning Config"
    override val modelsCollapse = "Collapse"
    override val modelsExpand = "Expand"
    override val modelsContext = "Context"
    override val modelsVision = "Multimodal"
    override val modelsTools = "Tools"
    override val modelsReasoning = "Reasoning"
    override val modelsNoModels = "No models"
    override val modelsTesting = "Testing…"
    override val modelsConnectionOk = "Connected"
    override val modelsConnectionFailed = "Connection failed"
    override val modelsRefreshOfficial = "Refresh Official Models"
    override val modelsFetchingOfficial = "Fetching available models from Google official services…"
    override fun modelsOfficialSyncFailed(error: String) = "Official model sync failed: $error"
    override val modelsOfficialSyncing = "Syncing official model catalog..."
    override val modelsOfficialSynced = "Official model catalog synchronized"
    override val modelsOfficialWaitingSync = "Waiting to sync official model catalog"
    override val modelsRawJson = "Raw JSON"
    override val modelsModifiedJson = "Modified JSON"
    override val modelsNoOfficialDetected = "No official models detected"
    override val modelsNoOfficialHint =
        "Please sign in or import an active account in Accounts page, then click Refresh"
    override val modelsNoAccountTitle = "No Active Account Found"
    override val modelsNoAccountHint =
        "Please sign in or import an account first. Official models and quotas will be automatically synchronized."
    override val modelsGoToAccounts = "Import / Sign In"
    override val modelsCopiedProviderUrl = "Provider endpoint copied to clipboard"
    override val modelsCopiedJson = "JSON data copied to clipboard"
    override fun modelsPassedCount(passed: Int, total: Int) = "$passed/$total passed"
    override fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int) = "$passed/$total passed ($failed failed)"
    override fun modelsRetryFailed(count: Int) = "Retry Failed ($count)"
    override val modelsBatchTest = "Batch Test"
    override val modelsEditConfig = "Edit Config"
    override val modelsNoModelsHint = "No models configured for this provider. Click Edit Config to add or fetch."
    override val modelsDeleteProviderConfirmTitle = "Delete Provider"
    override fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int) =
        "Are you sure you want to delete provider \"$providerName\"? All $modelCount associated models will be removed."

    override val modelsDeleteModelConfirmTitle = "Delete Model"
    override fun modelsDeleteModelConfirmMessage(modelName: String) =
        "Are you sure you want to delete model \"$modelName\"?"

    override val modelsRawJsonTitle = "Official Models Raw JSON Data"
    override val modelsModifiedJsonTitle = "Modified Official Models (Injected to IDE) JSON Data"
    override val modelsJsonData = "JSON Data"
    override fun modelsPolicyCapacityWithPrep(limit: String, prep: String) = "$limit capacity ($prep reserve)"
    override val modelsOfficialDefault = "Official Default"
    override val modelsVisionDesc = "Multimodal Capability"
    override val modelsToolsDesc = "Native Tool / Function Calling"
    override val modelsSpecsDesc = "View Model Specifications"
    override fun modelsTestSuccess(latency: String) = "Test Succeeded ($latency)"
    override val modelsTestFailed = "Test Failed"
    override val modelsEnabledDesc = "Enabled (Click to disable)"
    override val modelsDisabledDesc = "Disabled (Click to enable)"
    override val modelsEditModel = "Edit Model Config"
    override val modelsDeleteModel = "Delete Model"
    override val modelsReasoningLevelLabel = "Reasoning Level"
    override val modelsCompressionPolicyLabel = "Compression Policy"
    override val modelsEditPolicy = "Edit Policy"
    override val modelsCopyJson = "Copy JSON"
    override val modelsNotSet = "Not Set"

    override fun modelsProviderSaved(name: String) = "Saved provider \"$name\""
    override fun modelsProviderSaveFailed(error: String) = "Failed to save provider: $error"
    override fun modelsProviderDeleted(name: String) = "Deleted provider \"$name\""
    override fun modelsProviderDeleteFailed(error: String) = "Failed to delete provider: $error"
    override fun modelsModelDeleted(name: String) = "Deleted model \"$name\""
    override fun modelsModelDeleteFailed(error: String) = "Failed to delete model: $error"
    override fun modelsModelUpdated(name: String) = "Updated model \"$name\" configuration"
    override fun modelsModelUpdateFailed(error: String) = "Failed to update model: $error"
    override fun modelsModelTestSuccess(modelName: String, latencyMs: Long) =
        "$modelName test succeeded (${latencyMs}ms)"

    override fun modelsModelTestFailed(modelName: String, error: String) = "$modelName test failed: $error"
    override fun modelsBatchTestSuccess(passed: Int, total: Int) = "Provider test complete: $passed/$total tests passed"
    override fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int) =
        "Provider test complete: $passed/$total passed, $failed failed"

    override val modelsProviderNotFound = "Associated provider not found for this model"
    override val providerPresetCustomName = "Custom"
    override val providerPresetCustomDesc = "Manually configure any OpenAI-compatible, Anthropic or Gemini service"

    override val modelReasoningTitle = "Deep Thinking & Reasoning"
    override val modelReasoningDesc =
        "This model supports deep thinking / reasoning chains. During collaborative sessions with the IDE, the model can reason through complex architecture and logic:"

    override fun modelReasoningLevel(level: String) = "Level: $level"
    override val modelReasoningHighDesc = "High budget thinking (Best for complex algorithms & large refactoring)"
    override val modelReasoningMediumDesc = "Standard thinking (Balanced reasoning depth and latency)"
    override val modelReasoningLowDesc = "Lightweight thinking (Fast response with concise reasoning)"
    override val modelReasoningAdaptiveDesc = "Native adaptive deep thinking"
    override val modelVisionTitle = "Multimodal Input Support"
    override val modelVisionDesc =
        "Multimodal capability enables direct analysis of visual screenshots, design assets, diagrams and code references:"
    override val modelVisionImageTitle = "Image Analysis (Vision)"
    override val modelVisionImageDesc = "Upload PNG / JPEG / WEBP designs and UI error screenshots for direct analysis"
    override val modelVisionDocTitle = "Document Understanding"
    override val modelVisionDocDesc = "Native reading of PDF / text specifications to extract code context"
    override val modelToolsFunctionTitle = "Function Calling"
    override val modelToolsFunctionDesc = "Automated execution of IDE tools and terminal command orchestration"
    override val modelSpecsTitle = "Model Specifications & Metadata"
    override val modelSpecsId = "Model Identifier (ID)"
    override val modelSpecsContextWindow = "Total Context Window"
    override val modelSpecsDynamicConfig = "Official Dynamic Config"
    override val modelSpecsMaxOutput = "Max Output Tokens"
    override val modelSpecsDefaultLimit = "Official Default Limit"
    override val modelSpecsRoles = "Assigned Roles"

    override val policyPresetDefault = "Official Default"
    override val policyPresetCustom = "Custom"
    override val policyRecommended = "Recommended"
    override val policyDefaultDesc = "No custom override; follows native Checkpointer policy from official catalog."
    override val policyCustomDesc =
        "Click percentages for quick setup or enter exact token numbers for fine-tuned control."
    override val policyPresetDesc =
        "Choose a context limit tier: fixed presets are ready to use, or switch to custom policy."
    override val policyCompressorModel = "Compression Execution Model"
    override val policyFollowCurrent = "Follow Current Model"
    override val policyOfficialDefault = "Official Default"
    override val policyCheckpoint = "Auto Checkpoint"
    override val policyContextLimit = "Context Capacity"
    override val policyOutputReserve = "Output Reserve"
    override val policyByPercentage = "By Percentage"
    override val policyExactTokens = "Exact Tokens"
    override val policyQuickPreset = "Quick Presets"
    override val policyDistribution = "Context Capacity Distribution"
    override val policyLegendNormal = "Active Conversation Area"
    override val policyLegendArchive = "Auto Checkpoint Area (No deletion)"
    override val policyLegendUnused = "Unallocated Model Capacity"
    override fun policyModelContext(context: String) = "Model Context · $context"
    override val policyLimitMustPositive = "Context capacity must be greater than 0"
    override val policyThresholdMustPositive = "Auto checkpoint must be greater than 0"
    override val policyReserveMustPositive = "Output reserve must be greater than 0"
    override fun policyLimitExceedsContext(limit: String, context: String) =
        "Context capacity ($limit) cannot exceed model context ($context)"

    override fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String) =
        "Context capacity ($limit) exceeds safe limit ($safeLimit) [Formula: Model Context $context - Output Reserve $reserve]"

    override fun policyThresholdExceedsLimit(threshold: String, limit: String) =
        "Auto checkpoint ($threshold) must be less than context capacity ($limit)"

    override fun policySumExceedsLimit(sum: String, limit: String) =
        "Sum of checkpoint and reserve ($sum) exceeds context capacity ($limit)"

    override val policyFormulaHint =
        "Constraint: Trigger Limit (MaxTokenLimit) ≤ Model Context (ContextWindow) - Max Output (OutputLimit)"
    override val policyFormulaHintDesc =
        "The client strictly requires reserving output headroom for model responses, otherwise conversations fail immediately."

    override val reasoningDialogTitle = "Configure Deep Thinking"
    override val reasoningEnableTitle = "Enable Deep Thinking (Reasoning)"
    override val reasoningEnableSubtitle = "Enable and configure reasoning tiers and thinking budgets for this model"
    override val reasoningAvailableLevels = "Available Reasoning Levels"
    override val reasoningCustomValue = "Custom Reasoning Override"
    override val reasoningOptional = "Optional"
    override val reasoningCustomValueDesc = "Override with custom upstream reasoning strings if required"
    override val reasoningDefaultBudget = "Default Thinking Budget (Optional)"
    override val reasoningDynamicBudgetPlaceholder = "-1 for dynamic budget"
    override val reasoningMinBudgetTitle = "Min Thinking Budget (Optional)"
    override fun reasoningExamplePlaceholder(example: String) = "e.g. $example"
    override val reasoningBudget = "Thinking Budget"
    override val reasoningMinBudget = "Minimum Thinking Budget"
    override fun reasoningMustBeInteger(label: String) = "$label must be an integer"
    override val reasoningGeminiBudgetValidation = "Gemini thinking budget must be -1, 0 or a positive integer"
    override val reasoningMinBudgetMustPositive = "Minimum thinking budget must be greater than 0"
    override val reasoningMinBudgetExceedsBudget = "Minimum thinking budget cannot exceed thinking budget"
    override val reasoningOnlyGeminiSupportsBudget = "Only Gemini protocol supports model-level thinking budget"
    override val reasoningCustomValueInvalid =
        "Custom reasoning value does not conform to protocol or output constraints"

    override fun reasoningLevelInvalid(level: String) =
        "Reasoning level $level does not conform to protocol or output constraints"

    override val reasoningSelectAtLeastOne = "Please select at least one reasoning level or specify a thinking budget"

}
