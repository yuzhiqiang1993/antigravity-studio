package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.i18n.AppLanguage
import com.yuzhiqiang.antigravity.ui.screens.usage.UsageNumberFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageNumberFormatterTest {
    @Test
    fun chineseTokensUsePluginWanAndYiRules() {
        assertEquals("3894", UsageNumberFormatter.formatTokens(3_894, AppLanguage.ZH_CN))
        assertEquals("4.11 万", UsageNumberFormatter.formatTokens(41_090, AppLanguage.ZH_CN))
        assertEquals("15.02 万", UsageNumberFormatter.formatTokens(150_210, AppLanguage.ZH_CN))
        assertEquals("2.16 亿", UsageNumberFormatter.formatTokens(216_000_000, AppLanguage.ZH_CN))
        assertEquals("1 万", UsageNumberFormatter.formatTokens(10_000, AppLanguage.ZH_CN))
        assertEquals("9670 万", UsageNumberFormatter.formatTokens(96_700_000, AppLanguage.ZH_CN))
        assertEquals("5766.42 万", UsageNumberFormatter.formatTokens(57_664_239, AppLanguage.ZH_CN))
        assertEquals("4.82 亿", UsageNumberFormatter.formatTokens(481_733_158, AppLanguage.ZH_CN))
        assertEquals("1 亿", UsageNumberFormatter.formatTokens(100_000_000, AppLanguage.ZH_CN))
    }

    @Test
    fun englishTokensUsePluginKmbRules() {
        assertEquals("3.9K", UsageNumberFormatter.formatTokens(3_894, AppLanguage.EN_US))
        assertEquals("41.1K", UsageNumberFormatter.formatTokens(41_090, AppLanguage.EN_US))
        assertEquals("150.2K", UsageNumberFormatter.formatTokens(150_210, AppLanguage.EN_US))
        assertEquals("2.16B", UsageNumberFormatter.formatTokens(2_160_000_000, AppLanguage.EN_US))
        assertEquals("1.0M", UsageNumberFormatter.formatTokens(1_000_000, AppLanguage.EN_US))
    }

    @Test
    fun usdFormattingUsesPluginPrecision() {
        assertEquals("0", UsageNumberFormatter.formatUsdAmount(0.009))
        assertEquals("0.42", UsageNumberFormatter.formatUsdAmount(0.42))
        assertEquals("7.0", UsageNumberFormatter.formatUsdAmount(7.04))
        assertEquals("16", UsageNumberFormatter.formatUsdAmount(15.6))
    }
}
