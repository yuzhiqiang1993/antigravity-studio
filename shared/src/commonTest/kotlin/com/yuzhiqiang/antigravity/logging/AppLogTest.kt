package com.yuzhiqiang.antigravity.logging

import co.touchlab.kermit.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogTest {

    @Test
    fun testMaskEmail() {
        assertEquals("<empty>", AppLog.maskEmail(null))
        assertEquals("<empty>", AppLog.maskEmail(""))
        assertEquals("yuz***@gmail.com", AppLog.maskEmail("yuzhiqiang@gmail.com"))
        assertEquals("a***@domain.com", AppLog.maskEmail("ab@domain.com"))
        assertEquals("***", AppLog.maskEmail("invalid-email"))
    }

    @Test
    fun testMaskToken() {
        assertEquals("<empty>", AppLog.maskToken(null))
        assertEquals("<empty>", AppLog.maskToken(""))
        assertEquals("***", AppLog.maskToken("12345"))
        assertEquals("***abcdef", AppLog.maskToken("ya29.a0AfH6SMB_someLongSecretKey_abcdef"))
    }

    @Test
    fun testDebugModeToggleAndSilence() {
        val originalDebug = AppLog.isDebug
        try {
            // 1. 关闭 Debug 模式 (生产打包默认状态) -> 应当完全静默，零求值
            AppLog.isDebug = false
            assertFalse(AppLog.isEnabled)

            var evaluatedInSilent = false
            AppLog.d("Test/Silent") {
                evaluatedInSilent = true
                "This should not be evaluated"
            }
            assertFalse(evaluatedInSilent, "静默模式下 Debug Lambda 不应被求值计算")

            // 2. 开启 Debug 模式 -> 应当正常求值
            AppLog.isDebug = true
            assertTrue(AppLog.isEnabled)
            AppLog.minSeverity = Severity.Debug

            var evaluatedInDebug = false
            AppLog.d("Test/Debug") {
                evaluatedInDebug = true
                "This should be evaluated"
            }
            assertTrue(evaluatedInDebug, "Debug 模式下 Lambda 应当被求值")
        } finally {
            AppLog.isDebug = originalDebug
        }
    }

    @Test
    fun testLoggingExecutionDoesNotThrow() {
        val originalDebug = AppLog.isDebug
        try {
            AppLog.isDebug = true
            AppLog.minSeverity = Severity.Debug

            var evaluated = false
            AppLog.d("Test/Tag") {
                evaluated = true
                "Debug message"
            }
            assertTrue(evaluated, "Debug lambda 应当被执行")

            AppLog.i("Test/Tag") { "Info message" }
            AppLog.w("Test/Tag") { "Warn message" }
            AppLog.e("Test/Tag", RuntimeException("Test exception")) { "Error message" }

            val named = AppLog.withTag("Named/Tag")
            named.d { "Named debug" }
            named.i { "Named info" }
            named.w { "Named warn" }
            named.e(RuntimeException("Named error")) { "Named error" }
        } finally {
            AppLog.isDebug = originalDebug
        }
    }
}
