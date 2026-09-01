package com.yuzhiqiang.antigravity.studio

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingConfigTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun testDefaultConfigHasCompletedOnboardingIsFalse() {
        val config = AppConfig()
        assertFalse(config.hasCompletedOnboarding, "全新安装默认未完成新手引导")
    }

    @Test
    fun testSchemaV2JsonWithoutOnboardingFieldDefaultsToFalse() {
        val configJson = """
            {
                "schema_version": 2,
                "proxy_port": 8321,
                "language": "zh-CN"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<AppConfig>(configJson)
        assertFalse(decoded.hasCompletedOnboarding, "未设置新手引导状态时默认应为 false")
    }

    @Test
    fun testJsonWithOnboardingFieldTrueDecodesCorrectly() {
        val modernJson = """
            {
                "schema_version": 2,
                "proxy_port": 8321,
                "has_completed_onboarding": true
            }
        """.trimIndent()
        val decoded = json.decodeFromString<AppConfig>(modernJson)
        assertTrue(decoded.hasCompletedOnboarding, "完成新手引导后字段应正确解析为 true")
    }
}
