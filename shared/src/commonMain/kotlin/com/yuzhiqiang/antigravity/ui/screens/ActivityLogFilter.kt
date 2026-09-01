package com.yuzhiqiang.antigravity.ui.screens

import com.yuzhiqiang.antigravity.domain.model.ActivityLog

internal const val OFFICIAL_ROUTE_KEY = "__official_passthrough__"
internal const val UNKNOWN_ROUTE_KEY = "__unknown_provider__"

internal enum class ActivityClientKind {
    IDE,
    CLI,
    APP,
    PLUGIN,
    OTHER
}

internal enum class ActivityStatusKind {
    SUCCESS,
    FAILED,
    PENDING,
    RETRIED;

    fun matches(log: ActivityLog): Boolean = when (this) {
        SUCCESS -> !log.isPending && log.statusCode in 200..399
        FAILED -> !log.isPending && log.statusCode >= 400
        PENDING -> log.isPending
        RETRIED -> log.retryCount > 0
    }
}

/**
 * 接口业务语义分类
 */
internal enum class ActivityEndpointCategory {
    AI_CHAT,      // 核心大模型会话/生成 (streamGenerateContent, generateContent 等)
    CODE_ASSIST,  // 代码智能补全 (completeCode 等)
    SYSTEM        // 系统通信、指标打点、心跳轮询 (recordMetrics, fetchModels, quotas 等)
}

internal data class ActivityEndpointInfo(
    val rawPath: String,
    val displayName: String,
    val category: ActivityEndpointCategory,
    val description: String
)

internal object ActivityEndpointRegistry {
    fun resolve(path: String): ActivityEndpointInfo {
        val clean = cleanEndpointDisplayPath(path).lowercase()
        return when {
            clean.contains("streamgeneratecontent") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "流式内容生成",
                category = ActivityEndpointCategory.AI_CHAT,
                description = "核心大模型对话与流式文本生成"
            )

            clean.contains("generatecontent") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "内容生成 (非流式)",
                category = ActivityEndpointCategory.AI_CHAT,
                description = "大模型单次文本生成"
            )

            clean.contains("completecode") || clean.contains("inlinecompletion") || clean.contains("getcompletions") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "代码智能补全",
                category = ActivityEndpointCategory.CODE_ASSIST,
                description = "编辑器代码补全与续写"
            )

            clean.contains("recordcodeassistmetrics") || clean.contains("recordmetrics") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "指标数据上报",
                category = ActivityEndpointCategory.SYSTEM,
                description = "客户端性能监控与埋点"
            )

            clean.contains("fetchavailablemodels") || clean.contains("models") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "可用模型拉取",
                category = ActivityEndpointCategory.SYSTEM,
                description = "获取支持的模型目录"
            )

            clean.contains("retrieveuserquotassummary") || clean.contains("quotas") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "账号配额同步",
                category = ActivityEndpointCategory.SYSTEM,
                description = "查询当前生效账号配额"
            )

            clean.contains("fetchuserinfo") || clean.contains("userinfo") || clean.contains("getuserstatus") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "用户资料同步",
                category = ActivityEndpointCategory.SYSTEM,
                description = "用户账号信息获取"
            )

            clean.contains("listexperiments") || clean.contains("experiments") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "实验特性检测",
                category = ActivityEndpointCategory.SYSTEM,
                description = "灰度特性开关同步"
            )

            clean.contains("cascadenuxes") || clean.contains("nuxes") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "新手指引状态",
                category = ActivityEndpointCategory.SYSTEM,
                description = "新功能引导与打点"
            )

            clean.contains("loadcodeassist") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "辅助模块配置",
                category = ActivityEndpointCategory.SYSTEM,
                description = "IDE 辅助配置加载"
            )

            clean.contains("fetchadmincontrols") -> ActivityEndpointInfo(
                rawPath = path,
                displayName = "企业策略拉取",
                category = ActivityEndpointCategory.SYSTEM,
                description = "管理员控制策略"
            )

            else -> ActivityEndpointInfo(
                rawPath = path,
                displayName = cleanEndpointDisplayPath(path),
                category = if (clean.contains("chat") || clean.contains("generate") || clean.contains("complet")) {
                    ActivityEndpointCategory.AI_CHAT
                } else {
                    ActivityEndpointCategory.SYSTEM
                },
                description = path
            )
        }
    }
}

internal data class ActivityLogFilter(
    val clients: Set<ActivityClientKind> = emptySet(),
    val endpoints: Set<String> = emptySet(),
    val routes: Set<String> = emptySet(),
    val statuses: Set<ActivityStatusKind> = emptySet(),
    val onlyAiChat: Boolean = false
) {
    val activeCount: Int
        get() = clients.size + endpoints.size + routes.size + statuses.size + (if (onlyAiChat) 1 else 0)

    val isActive: Boolean
        get() = activeCount > 0
}

internal fun ActivityLog.clientKind(): ActivityClientKind {
    val normalized = clientSource.orEmpty().lowercase()
    val tokens = normalized.split(NON_ALPHANUMERIC_REGEX).filter { it.isNotEmpty() }.toSet()
    return when {
        "cockpit" in tokens || "plugin" in tokens -> ActivityClientKind.PLUGIN
        "ide" in tokens || "vscode" in tokens || "codeium" in tokens -> ActivityClientKind.IDE
        "cli" in tokens || "agy" in tokens || "terminal" in tokens -> ActivityClientKind.CLI
        "app" in tokens || "hub" in tokens || "desktop" in tokens || "electron" in tokens -> ActivityClientKind.APP
        else -> ActivityClientKind.OTHER
    }
}

internal fun ActivityLog.isAiChatRequest(): Boolean {
    val endpointInfo = ActivityEndpointRegistry.resolve(path)
    return endpointInfo.category == ActivityEndpointCategory.AI_CHAT ||
            endpointInfo.category == ActivityEndpointCategory.CODE_ASSIST
}

internal fun ActivityLog.routeKey(): String = when {
    isOfficialPassthrough -> OFFICIAL_ROUTE_KEY
    providerName.isNullOrBlank() -> UNKNOWN_ROUTE_KEY
    else -> providerName.trim()
}

internal fun filterActivityLogs(
    logs: List<ActivityLog>,
    query: String,
    filter: ActivityLogFilter,
    additionalSearchTerms: (ActivityLog) -> List<String> = { emptyList() }
): List<ActivityLog> {
    val normalizedQuery = query.trim().lowercase()
    return logs.filter { log ->
        val matchesAiChat = !filter.onlyAiChat || log.isAiChatRequest()
        val matchesClient = filter.clients.isEmpty() || log.clientKind() in filter.clients
        val matchesEndpoint = filter.endpoints.isEmpty() || log.path in filter.endpoints
        val matchesRoute = filter.routes.isEmpty() || log.routeKey() in filter.routes
        val matchesStatus = filter.statuses.isEmpty() || filter.statuses.any { it.matches(log) }
        val endpointInfo = ActivityEndpointRegistry.resolve(log.path)
        val matchesQuery = normalizedQuery.isEmpty() || buildList {
            add(log.method)
            add(log.path)
            add(endpointInfo.displayName)
            add(endpointInfo.description)
            add(log.statusCode.toString())
            listOfNotNull(
                log.clientSource,
                log.providerName,
                log.errorMessage,
                log.errorSource
            ).forEach(::add)
            addAll(log.modelIdentity?.searchTerms.orEmpty())
            addAll(additionalSearchTerms(log))
        }.any { it.lowercase().contains(normalizedQuery) }

        matchesAiChat && matchesClient && matchesEndpoint && matchesRoute && matchesStatus && matchesQuery
    }
}

internal fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
