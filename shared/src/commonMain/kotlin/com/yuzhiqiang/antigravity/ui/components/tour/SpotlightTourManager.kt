package com.yuzhiqiang.antigravity.ui.components.tour

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.yuzhiqiang.antigravity.ui.presentation.NavTab

/**
 * 聚光灯新手引导步骤枚举（两阶段 12 步超详细深度漫游体系）
 *
 * @param order 步骤序号 (1..12)
 * @param tab 所在页面导航 Tab
 * @param isSidePlacement 是否停靠在侧边栏右侧（前 5 步为 true，后 7 步为 false）
 * @param associatedSidebarStep 阶段二介绍右侧详情时，左侧需同步高亮镂空的对应导航 Tab
 */
enum class TourStep(
    val order: Int,
    val tab: NavTab,
    val isSidePlacement: Boolean,
    val associatedSidebarStep: TourStep? = null
) {
    // 阶段一：左侧导航全景骨架介绍（侧边栏右侧停靠气泡）
    SIDEBAR_OVERVIEW(1, NavTab.OVERVIEW, true),
    SIDEBAR_ACCOUNTS(2, NavTab.OVERVIEW, true),
    SIDEBAR_MODELS(3, NavTab.OVERVIEW, true),
    SIDEBAR_ACTIVITY(4, NavTab.OVERVIEW, true),
    SIDEBAR_SETTINGS(5, NavTab.OVERVIEW, true),

    // 阶段二：右侧功能页核心深度聚焦（同步高亮左侧导航 Tab）
    OVERVIEW_HERO_PROXY(6, NavTab.OVERVIEW, false, SIDEBAR_OVERVIEW),
    OVERVIEW_HOST_GRID(7, NavTab.OVERVIEW, false, SIDEBAR_OVERVIEW),
    ACCOUNTS_MANAGE(8, NavTab.ACCOUNTS, false, SIDEBAR_ACCOUNTS),
    MODELS_MANAGE(9, NavTab.MODELS, false, SIDEBAR_MODELS),
    ACTIVITY_PANEL(10, NavTab.ACTIVITY, false, SIDEBAR_ACTIVITY),
    SETTINGS_PANEL(11, NavTab.SETTINGS, false, SIDEBAR_SETTINGS),
    ABOUT_REOPEN_CARD(12, NavTab.SETTINGS, false, SIDEBAR_SETTINGS);

    fun next(): TourStep? {
        val nextOrder = order + 1
        return entries.firstOrNull { it.order == nextOrder }
    }

    fun prev(): TourStep? {
        val prevOrder = order - 1
        return entries.firstOrNull { it.order == prevOrder }
    }
}

/**
 * 聚光灯新手引导全局状态调度器（支持两阶段跨页面平滑漫游联动）
 */
class SpotlightTourManager {
    var isActive by mutableStateOf(false)
        private set

    var currentStep by mutableStateOf(TourStep.SIDEBAR_OVERVIEW)
        private set

    var onNavigateTab: ((NavTab) -> Unit)? = null

    val anchors = mutableStateMapOf<TourStep, Rect>()

    fun startTour() {
        currentStep = TourStep.SIDEBAR_OVERVIEW
        isActive = true
        onNavigateTab?.invoke(TourStep.SIDEBAR_OVERVIEW.tab)
    }

    fun nextStep(onComplete: () -> Unit) {
        val next = currentStep.next()
        if (next != null) {
            currentStep = next
            onNavigateTab?.invoke(next.tab)
        } else {
            finishTour(onComplete)
        }
    }

    fun prevStep() {
        val prev = currentStep.prev()
        if (prev != null) {
            currentStep = prev
            onNavigateTab?.invoke(prev.tab)
        }
    }

    fun finishTour(onComplete: () -> Unit) {
        isActive = false
        onNavigateTab?.invoke(NavTab.OVERVIEW)
        onComplete()
    }

    fun skipTour(onComplete: () -> Unit) {
        isActive = false
        onNavigateTab?.invoke(NavTab.OVERVIEW)
        onComplete()
    }

    fun registerAnchor(step: TourStep, rect: Rect) {
        anchors[step] = rect
    }
}

val LocalSpotlightTourManager = compositionLocalOf<SpotlightTourManager> {
    error("No SpotlightTourManager provided")
}

/**
 * 为组件注册聚光灯目标坐标的便捷 Modifier
 */
fun Modifier.tourAnchor(step: TourStep, manager: SpotlightTourManager): Modifier {
    return this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (bounds.width > 0 && bounds.height > 0) {
            manager.registerAnchor(step, bounds)
        }
    }
}
