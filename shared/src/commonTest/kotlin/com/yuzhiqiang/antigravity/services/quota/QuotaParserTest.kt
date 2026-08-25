package com.yuzhiqiang.antigravity.services.quota

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import com.yuzhiqiang.antigravity.domain.model.quota.AccountQuotaSnapshot
import com.yuzhiqiang.antigravity.domain.model.quota.ModelQuotaInfo
import com.yuzhiqiang.antigravity.domain.model.quota.QuotaWindow
import kotlin.test.*

class QuotaParserTest {

    @Test
    fun testModelQuotaInfoCountdown() {
        val nowSec = System.currentTimeMillis() / 1000L
        val quota1 = ModelQuotaInfo(
            id = "claude-3-7-sonnet",
            displayName = "Claude 3.7 Sonnet",
            family = "claude",
            window = QuotaWindow.FIVE_HOUR,
            remainingFraction = 0.75,
            resetTimeEpochSeconds = nowSec + 3600L + 600L // 1小时10分后
        )

        assertEquals(75, quota1.percentage)
        assertFalse(quota1.isExhausted)
        val countdown = quota1.formattedCountdown()
        assertNotNull(countdown)
        assertTrue(countdown.contains("小时") || countdown.contains("分钟"))


        val exhaustedQuota = ModelQuotaInfo(
            id = "claude-3-5-haiku",
            displayName = "Claude 3.5 Haiku",
            family = "claude",
            window = QuotaWindow.FIVE_HOUR,
            remainingFraction = 0.0,
            resetTimeEpochSeconds = nowSec + 120L // 2分钟后
        )
        assertTrue(exhaustedQuota.isExhausted)
        assertEquals(0, exhaustedQuota.percentage)
        assertEquals("2分钟", exhaustedQuota.formattedCountdown())
        assertEquals("您已消耗部分五小时额度，将在 2分钟 后完全重置。", exhaustedQuota.naturalLanguageDescription())
    }


    @Test
    fun testPrimaryQuotasExtraction() {
        val snapshot = AccountQuotaSnapshot(
            accountId = "acc_test",
            email = "user@gmail.com",
            tierName = "Antigravity Pro",
            isPro = true,
            models = listOf(
                ModelQuotaInfo("gemini-2.5-flash", "Gemini 2.5 Flash", "gemini", remainingFraction = 1.0),
                ModelQuotaInfo("claude-3-7-sonnet", "Claude 3.7 Sonnet", "claude", remainingFraction = 0.8),
                ModelQuotaInfo("gemini-2.5-pro", "Gemini 2.5 Pro", "gemini", remainingFraction = 0.95),
                ModelQuotaInfo("gpt-4o", "GPT-4o", "gpt", remainingFraction = 0.5)
            )
        )

        val claude = snapshot.claudeQuota()
        assertNotNull(claude)
        assertEquals("claude-3-7-sonnet", claude.id)

        val geminiPro = snapshot.geminiProQuota()
        assertNotNull(geminiPro)
        assertEquals("gemini-2.5-pro", geminiPro.id)

        val geminiFlash = snapshot.geminiFlashQuota()
        assertNotNull(geminiFlash)
        assertEquals("gemini-2.5-flash", geminiFlash.id)

        val primaries = snapshot.primaryQuotas()
        assertEquals(3, primaries.size)
        assertEquals("claude-3-7-sonnet", primaries[0].id)
        assertEquals("gemini-2.5-pro", primaries[1].id)
        assertEquals("gemini-2.5-flash", primaries[2].id)
    }

    @Test
    fun testParseQuotaFromOfficialCatalogJson() {
        val service = QuotaFetchService()
        val account = AccountInfo(
            id = "acc_1",
            profile = AccountProfile("test@antigravity.ai"),
            tokens = OAuthTokens("access", "refresh", 9999999999L)
        )

        val mockCatalogJson = """
            {
              "response": {
                "models": {
                  "claude-3-7-sonnet": {
                    "displayName": "Claude 3.7 Sonnet",
                    "quotaInfo": {
                      "remainingFraction": 0.85,
                      "resetTime": "2026-08-25T16:00:00Z"
                    }
                  },
                  "gemini-2.5-pro": {
                    "displayName": "Gemini 2.5 Pro",
                    "quotaInfo": {
                      "remainingFraction": 0.50,
                      "resetTime": "2026-08-25T18:00:00Z"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val snapshot = service.parseQuotaFromOfficialCatalogJson(account, mockCatalogJson)
        assertEquals("acc_1", snapshot.accountId)
        assertEquals("test@antigravity.ai", snapshot.email)
        assertEquals(2, snapshot.models.size)

        val claude = snapshot.models.first { it.id == "claude-3-7-sonnet" }
        assertEquals(85, claude.percentage)
        assertNotNull(claude.resetTimeEpochSeconds)

        val gemini = snapshot.models.first { it.id == "gemini-2.5-pro" }
        assertEquals(50, gemini.percentage)
    }
}
