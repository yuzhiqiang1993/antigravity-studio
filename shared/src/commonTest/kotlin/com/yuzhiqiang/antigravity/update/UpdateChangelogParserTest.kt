package com.yuzhiqiang.antigravity.update

import com.yuzhiqiang.antigravity.update.model.UpdateChangelogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateChangelogParserTest {

    @Test
    fun testParseBilingualChangelog() {
        val raw = """
            ## [1.3.2] - 2026-09-04
            
            ### 🇨🇳 中文
            
            #### ✨ 体验与界面优化
            - 精简调用详情弹窗性能指标
            - 用量成本呈现优化
            
            ---
            
            ### 🌐 English
            
            #### ✨ Improvements & Experience
            - Streamlined Request Details Dialog
            - Usage Cost Localization Tweaks
        """.trimIndent()

        val parsed = UpdateChangelogParser.parse(raw)
        assertTrue(parsed.hasBilingual)
        assertTrue(parsed.chineseContent!!.contains("精简调用详情弹窗性能指标"))
        assertFalse(parsed.chineseContent.contains("English"))
        assertTrue(parsed.englishContent!!.contains("Streamlined Request Details Dialog"))
        assertFalse(parsed.englishContent.contains("体验与界面优化"))
    }

    @Test
    fun testParseSingleLanguageChangelog() {
        val raw = """
            #### ✨ New Features
            - Added support for new models
        """.trimIndent()

        val parsed = UpdateChangelogParser.parse(raw)
        assertFalse(parsed.hasBilingual)
        assertEquals(raw, parsed.rawContent)
    }

    @Test
    fun testParseEmptyChangelog() {
        val parsed = UpdateChangelogParser.parse(null)
        assertFalse(parsed.hasBilingual)
        assertEquals("", parsed.rawContent)
    }
}
