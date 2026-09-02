package com.yuzhiqiang.antigravity.studio

import com.yuzhiqiang.antigravity.i18n.sections.CoreStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.CoreStringsZh
import com.yuzhiqiang.antigravity.ui.components.tour.TourStep
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotlightTourTest {

    @Test
    fun testTourStepCountAndOrderContinuity() {
        val entries = TourStep.entries
        assertEquals(14, entries.size, "新手指引体系应包含 14 个完整步骤")

        // 验证步骤序号从 1 到 14 严格连续无重复
        val orders = entries.map { it.order }
        assertEquals((1..14).toList(), orders, "TourStep 序号必须从 1 到 14 连续且有序")
    }

    @Test
    fun testTourStepTwoPhasesStructure() {
        val entries = TourStep.entries

        // 阶段一：前 6 步为侧边栏导航骨架漫游
        val phase1Steps = entries.take(6)
        assertEquals(
            listOf(
                TourStep.SIDEBAR_OVERVIEW,
                TourStep.SIDEBAR_ACCOUNTS,
                TourStep.SIDEBAR_MODELS,
                TourStep.SIDEBAR_USAGE,
                TourStep.SIDEBAR_ACTIVITY,
                TourStep.SIDEBAR_SETTINGS
            ),
            phase1Steps
        )
        phase1Steps.forEach { step ->
            assertTrue(step.isSidePlacement, "阶段一步骤 ${step.name} 必须为侧边栏停靠 (isSidePlacement = true)")
            assertNull(step.associatedSidebarStep, "阶段一步骤 ${step.name} 不应有 associatedSidebarStep")
        }

        // 阶段二：后 8 步为右侧页面核心功能深度聚焦
        val phase2Steps = entries.drop(6)
        assertEquals(
            listOf(
                TourStep.OVERVIEW_HERO_PROXY,
                TourStep.OVERVIEW_HOST_GRID,
                TourStep.ACCOUNTS_MANAGE,
                TourStep.MODELS_MANAGE,
                TourStep.USAGE_PANEL,
                TourStep.ACTIVITY_PANEL,
                TourStep.SETTINGS_PANEL,
                TourStep.ABOUT_REOPEN_CARD
            ),
            phase2Steps
        )
        phase2Steps.forEach { step ->
            assertFalse(step.isSidePlacement, "阶段二步骤 ${step.name} 不应为侧边栏停靠 (isSidePlacement = false)")
            val associated = step.associatedSidebarStep
            assertNotNull(associated, "阶段二步骤 ${step.name} 必须关联一个左侧导航步骤")
            assertTrue(associated.isSidePlacement, "关联的步骤 ${associated.name} 必须是阶段一的侧边栏步骤")
            assertTrue(associated.order <= 6, "关联的步骤 ${associated.name} 序号必须处于阶段一 (1..6)")
        }

        // 验证 USAGE_PANEL 与 SIDEBAR_USAGE 的绑定
        assertEquals(NavTab.USAGE, TourStep.USAGE_PANEL.tab)
        assertEquals(TourStep.SIDEBAR_USAGE, TourStep.USAGE_PANEL.associatedSidebarStep)
    }

    @Test
    fun testTourNavigationTransitions() {
        val firstStep = TourStep.SIDEBAR_OVERVIEW
        assertNull(firstStep.prev(), "第一步的 prev() 应为 null")
        assertEquals(TourStep.SIDEBAR_ACCOUNTS, firstStep.next())

        val lastStep = TourStep.ABOUT_REOPEN_CARD
        assertNull(lastStep.next(), "最后一步的 next() 应为 null")
        assertEquals(TourStep.SETTINGS_PANEL, lastStep.prev())

        // 验证整条链表的双向遍历一致性
        var current: TourStep? = firstStep
        var forwardCount = 0
        while (current != null) {
            forwardCount++
            val next = current.next()
            if (next != null) {
                assertEquals(current, next.prev(), "步进与回退应具有对称性")
            }
            current = next
        }
        assertEquals(14, forwardCount, "从第一步顺次遍历应恰好经历 14 步")
    }

    @Test
    fun testI18nTourStringsCompleteness() {
        val zhTitles = listOf(
            CoreStringsZh.tourStep1Title,
            CoreStringsZh.tourStep2Title,
            CoreStringsZh.tourStep3Title,
            CoreStringsZh.tourStep4Title,
            CoreStringsZh.tourStep5Title,
            CoreStringsZh.tourStep6Title,
            CoreStringsZh.tourStep7Title,
            CoreStringsZh.tourStep8Title,
            CoreStringsZh.tourStep9Title,
            CoreStringsZh.tourStep10Title,
            CoreStringsZh.tourStep11Title,
            CoreStringsZh.tourStep12Title,
            CoreStringsZh.tourStep13Title,
            CoreStringsZh.tourStep14Title
        )
        val zhDescs = listOf(
            CoreStringsZh.tourStep1Desc,
            CoreStringsZh.tourStep2Desc,
            CoreStringsZh.tourStep3Desc,
            CoreStringsZh.tourStep4Desc,
            CoreStringsZh.tourStep5Desc,
            CoreStringsZh.tourStep6Desc,
            CoreStringsZh.tourStep7Desc,
            CoreStringsZh.tourStep8Desc,
            CoreStringsZh.tourStep9Desc,
            CoreStringsZh.tourStep10Desc,
            CoreStringsZh.tourStep11Desc,
            CoreStringsZh.tourStep12Desc,
            CoreStringsZh.tourStep13Desc,
            CoreStringsZh.tourStep14Desc
        )

        val enTitles = listOf(
            CoreStringsEn.tourStep1Title,
            CoreStringsEn.tourStep2Title,
            CoreStringsEn.tourStep3Title,
            CoreStringsEn.tourStep4Title,
            CoreStringsEn.tourStep5Title,
            CoreStringsEn.tourStep6Title,
            CoreStringsEn.tourStep7Title,
            CoreStringsEn.tourStep8Title,
            CoreStringsEn.tourStep9Title,
            CoreStringsEn.tourStep10Title,
            CoreStringsEn.tourStep11Title,
            CoreStringsEn.tourStep12Title,
            CoreStringsEn.tourStep13Title,
            CoreStringsEn.tourStep14Title
        )
        val enDescs = listOf(
            CoreStringsEn.tourStep1Desc,
            CoreStringsEn.tourStep2Desc,
            CoreStringsEn.tourStep3Desc,
            CoreStringsEn.tourStep4Desc,
            CoreStringsEn.tourStep5Desc,
            CoreStringsEn.tourStep6Desc,
            CoreStringsEn.tourStep7Desc,
            CoreStringsEn.tourStep8Desc,
            CoreStringsEn.tourStep9Desc,
            CoreStringsEn.tourStep10Desc,
            CoreStringsEn.tourStep11Desc,
            CoreStringsEn.tourStep12Desc,
            CoreStringsEn.tourStep13Desc,
            CoreStringsEn.tourStep14Desc
        )

        assertEquals(14, zhTitles.size)
        assertEquals(14, zhDescs.size)
        assertEquals(14, enTitles.size)
        assertEquals(14, enDescs.size)

        zhTitles.forEachIndexed { index, title ->
            assertTrue(title.isNotBlank(), "中文步骤 ${index + 1} 标题不可为空白")
        }
        zhDescs.forEachIndexed { index, desc ->
            assertTrue(desc.isNotBlank(), "中文步骤 ${index + 1} 描述不可为空白")
        }
        enTitles.forEachIndexed { index, title ->
            assertTrue(title.isNotBlank(), "英文步骤 ${index + 1} 标题不可为空白")
        }
        enDescs.forEachIndexed { index, desc ->
            assertTrue(desc.isNotBlank(), "英文步骤 ${index + 1} 描述不可为空白")
        }
    }
}
