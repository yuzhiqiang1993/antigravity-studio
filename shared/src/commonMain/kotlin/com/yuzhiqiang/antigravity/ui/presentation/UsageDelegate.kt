package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.usage.UsageRepository
import com.yuzhiqiang.antigravity.domain.model.usage.CustomDateRange
import com.yuzhiqiang.antigravity.domain.model.usage.DeepUsageStats
import com.yuzhiqiang.antigravity.domain.model.usage.UsageTimeRange
import com.yuzhiqiang.antigravity.i18n.currentStrings
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 用量统计表现层委托
 */
class UsageDelegate(
    private val scope: CoroutineScope,
    private val usageRepository: UsageRepository,
    private val showNotice: (String, NoticeKind) -> Unit
) {
    val usageStats: StateFlow<DeepUsageStats> = usageRepository.usageStats
    val isRefreshing: StateFlow<Boolean> = usageRepository.isRefreshing
    val selectedTimeRange: StateFlow<UsageTimeRange> = usageRepository.selectedTimeRange
    val customDateRange: StateFlow<CustomDateRange?> = usageRepository.customDateRange
    val selectedSources: StateFlow<Set<String>> = usageRepository.selectedSources

    private var initialRefreshStarted = false

    /** 在价格配置完成后启动首次扫描，避免首次聚合使用默认费率。 */
    fun startInitialRefresh() {
        if (initialRefreshStarted) return
        initialRefreshStarted = true
        com.yuzhiqiang.antigravity.logging.AppLog.i("Usage/Delegate") { "启动用量统计首次扫描 (force=false)" }
        scope.launch {
            usageRepository.refresh(force = false)
        }
    }

    /**
     * 手动触发刷新用量统计
     */
    fun refresh(force: Boolean = true) {
        initialRefreshStarted = true
        com.yuzhiqiang.antigravity.logging.AppLog.i("Usage/Delegate") { "用户手动触发刷新用量统计 (force=$force)" }
        scope.launch {
            val result = usageRepository.refresh(force = force)
            val s = currentStrings()
            result.onSuccess { stats ->
                com.yuzhiqiang.antigravity.logging.AppLog.i("Usage/Delegate") { "用量统计刷新成功: 会话总数=${stats.totalConversations}, 总Token=${stats.totalTokens}" }
                showNotice(s.usageRefreshSuccessNotice(stats.totalConversations), NoticeKind.SUCCESS)
            }.onFailure { error ->
                com.yuzhiqiang.antigravity.logging.AppLog.e("Usage/Delegate", error) { "用量统计刷新失败: ${error.message}" }
                showNotice(error.message ?: "刷新用量失败", NoticeKind.ERROR)
            }
        }
    }

    /**
     * 切换时间筛选范围
     */
    fun setTimeRange(timeRange: UsageTimeRange, customRange: CustomDateRange? = null) {
        scope.launch {
            usageRepository.setTimeRange(timeRange, customRange)
        }
    }

    /**
     * 切换来源筛选
     */
    fun toggleSource(source: String) {
        scope.launch {
            val current = selectedSources.value
            val next = if (source == "all") {
                setOf("all")
            } else {
                val withoutAll = current.filter { it != "all" }.toMutableSet()
                if (withoutAll.contains(source)) {
                    withoutAll.remove(source)
                } else {
                    withoutAll.add(source)
                }
                if (withoutAll.isEmpty()) setOf("all") else withoutAll
            }
            usageRepository.setSelectedSources(next)
        }
    }

    /**
     * 重新计算统计数据
     */
    fun recompute() {
        scope.launch {
            usageRepository.recomputeStats()
        }
    }
}
