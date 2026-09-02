package com.yuzhiqiang.antigravity.proxy

import com.yuzhiqiang.antigravity.domain.model.ActivityModelIdentity
import com.yuzhiqiang.antigravity.proxy.activity.ActivityRecorder
import com.yuzhiqiang.antigravity.proxy.model.NeutralUsage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityRecorderTest {

    @BeforeTest
    fun setup() {
        ActivityRecorder.clear()
    }

    @Test
    fun testStartAndUpdateAndFinishActivityLifecycle() {
        // 1. 请求刚到达时调用 startActivity
        val logId = ActivityRecorder.startActivity(
            method = "POST",
            path = "/v1internal:streamGenerateContent",
            modelIdentity = ActivityModelIdentity(
                requestedModelId = "gpt-5",
                variantId = "custom-gpt-5"
            ),
            clientSource = "Antigravity IDE",
            providerName = "OpenAI",
            isOfficialPassthrough = false,
            timestamp = 1000L,
            queueWaitMs = 120L
        )
        assertNotNull(logId)
        val initialLogs = ActivityRecorder.logs.value
        assertEquals(1, initialLogs.size)
        val pendingLog = initialLogs.first()
        assertEquals(logId, pendingLog.id)
        assertTrue(pendingLog.isPending)
        assertEquals(0, pendingLog.statusCode)
        assertEquals("OpenAI", pendingLog.providerName)
        assertEquals("Antigravity IDE", pendingLog.clientSource)

        // 2. 流式返回首字时更新 TTFT
        ActivityRecorder.updateFirstToken(logId, 1500L)
        ActivityRecorder.updateRetryCount(logId, 2)
        val intermediateLogs = ActivityRecorder.logs.value
        assertEquals(1, intermediateLogs.size)
        assertEquals(1500L, intermediateLogs.first().firstTokenMs)
        assertEquals(2, intermediateLogs.first().retryCount)
        assertTrue(intermediateLogs.first().isPending)

        // 3. 请求完成时更新 finishActivity
        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = 200,
            durationMs = 3500L,
            firstByteMs = 900L,
            lastTokenMs = 3500L,
            maxChunkGapMs = 450L,
            stallCount = 1,
            stallDurationMs = 2_100L,
            errorMessage = "upstream failed",
            errorSource = "UPSTREAM_RESPONSE",
            responseModelId = "gpt-5-2026-08-07",
            usage = NeutralUsage(
                inputTokens = 100,
                outputTokens = 200,
                unattributedTokens = 50,
                totalTokens = 350
            ),
            retryCount = 2,
            responseHeaders = mapOf("content-type" to "application/json"),
            responseBody = "{\"status\":\"ok\"}"
        )
        val finishedLogs = ActivityRecorder.logs.value
        assertEquals(1, finishedLogs.size)
        val finishedLog = finishedLogs.first()
        assertFalse(finishedLog.isPending)
        assertEquals(200, finishedLog.statusCode)
        assertEquals(3500L, finishedLog.durationMs)
        assertEquals(120L, finishedLog.queueWaitMs)
        assertEquals(900L, finishedLog.firstByteMs)
        assertEquals(1500L, finishedLog.firstTokenMs)
        assertEquals(3500L, finishedLog.lastTokenMs)
        assertEquals(2000L, finishedLog.generationDurationMs)
        assertEquals(100.0, finishedLog.tokensPerSecond)
        assertEquals(2000.0 / 199.0, finishedLog.timePerOutputTokenMs)
        assertEquals(450L, finishedLog.maxChunkGapMs)
        assertEquals(1, finishedLog.stallCount)
        assertEquals(2_100L, finishedLog.stallDurationMs)
        assertEquals(50L, finishedLog.unattributedTokens)
        assertEquals(350L, finishedLog.totalTokens)
        assertEquals("UPSTREAM_RESPONSE", finishedLog.errorSource)
        assertEquals("custom-gpt-5", finishedLog.modelIdentity?.variantId)
        assertEquals("gpt-5-2026-08-07", finishedLog.modelIdentity?.responseModelId)
        assertEquals(2, finishedLog.retryCount)
        assertEquals("{\"status\":\"ok\"}", finishedLog.responseBody)
        assertEquals("application/json", finishedLog.responseHeaders?.get("content-type"))
    }

    @Test
    fun testRecordDirectlyWithDebugPayloads() {
        ActivityRecorder.record(
            method = "GET",
            path = "/v1beta/models",
            modelIdentity = null,
            providerName = "Studio Local Catalog",
            statusCode = 200,
            durationMs = 50L,
            isOfficialPassthrough = false,
            requestHeaders = mapOf("accept" to "application/json"),
            requestBody = null,
            responseHeaders = mapOf("server" to "antigravity-studio"),
            responseBody = "{\"models\":[]}"
        )
        val logs = ActivityRecorder.logs.value
        assertEquals(1, logs.size)
        assertFalse(logs.first().isPending)
        assertEquals(200, logs.first().statusCode)
        assertEquals("application/json", logs.first().requestHeaders?.get("accept"))
        assertEquals("antigravity-studio", logs.first().responseHeaders?.get("server"))
        assertEquals("{\"models\":[]}", logs.first().responseBody)
    }

    @Test
    fun testDoesNotInventThroughputWithoutAUsableGenerationWindow() {
        val logId = ActivityRecorder.startActivity(
            method = "POST",
            path = "/v1/chat",
            modelIdentity = ActivityModelIdentity(variantId = "single-response"),
            providerName = "Test",
            isOfficialPassthrough = false
        )

        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = 200,
            durationMs = 500L,
            usage = NeutralUsage(outputTokens = 1),
            firstTokenMs = 500L,
            lastTokenMs = 500L
        )

        val log = ActivityRecorder.logs.value.single()
        assertEquals(0L, log.generationDurationMs)
        assertEquals(null, log.tokensPerSecond)
        assertEquals(null, log.timePerOutputTokenMs)
    }

    @Test
    fun testBurstOrSingleChunkResponseDoesNotExplodeTps() {
        val logId = ActivityRecorder.startActivity(
            method = "POST",
            path = "/v1internal:streamGenerateContent",
            modelIdentity = ActivityModelIdentity(variantId = "custom-gpt-5-6-sol-x-high"),
            providerName = "OpenAI",
            isOfficialPassthrough = false
        )

        // 模拟 19s 长思考后在 1ms 内突发返回 405 tokens（首字 19000ms，尾字 19000ms，总耗时 19001ms）
        ActivityRecorder.finishActivity(
            id = logId,
            statusCode = 200,
            durationMs = 19001L,
            usage = NeutralUsage(outputTokens = 405),
            firstTokenMs = 19000L,
            lastTokenMs = 19000L
        )

        val log = ActivityRecorder.logs.value.single()
        assertEquals(19001L, log.generationDurationMs)
        assertNotNull(log.tokensPerSecond)
        assertTrue(log.tokensPerSecond!! in 20.0..22.0) // 405 / 19.001 ≈ 21.31 t/s
    }

    @Test
    fun testLargePayloadSanitization() {
        val hugeBody = "A".repeat(1_200_000)
        val logId = ActivityRecorder.startActivity(
            method = "POST",
            path = "/v1/chat",
            modelIdentity = ActivityModelIdentity(variantId = "gpt-4"),
            providerName = "OpenAI",
            isOfficialPassthrough = false,
            requestBody = hugeBody
        )
        val log = ActivityRecorder.logs.value.first()
        assertNotNull(log.requestBody)
        assertTrue(log.requestBody!!.contains("... [Truncated:"))
        assertTrue(log.requestBody!!.length < 1_100_000)
    }

    @Test
    fun testAppConfigCollectNonChatLogsDefaultsToFalse() {
        val config = com.yuzhiqiang.antigravity.domain.model.AppConfig()
        assertFalse(config.collectNonChatLogs, "默认情况下 collectNonChatLogs 应当为 false，仅记录核心会话请求")
    }

    @Test
    fun testAiChatPathsAreRecognizedCorrectly() {
        fun isAiChatOrCompletion(path: String, modelId: String?): Boolean {
            if (!modelId.isNullOrBlank()) return true
            val lower = path.lowercase()
            return lower.contains("generatecontent") ||
                    lower.contains("streamgeneratecontent") ||
                    lower.contains("completecode") ||
                    lower.contains("inlinecompletion") ||
                    lower.contains("getcompletions") ||
                    lower.contains("chat")
        }

        // 核心会话和生成类路径
        assertTrue(isAiChatOrCompletion("/v1internal:streamGenerateContent", null))
        assertTrue(isAiChatOrCompletion("/v1beta/models/gemini-2.5-pro:generateContent", null))
        assertTrue(isAiChatOrCompletion("/v1internal:completeCode", null))
        assertTrue(isAiChatOrCompletion("/v1/chat/completions", null))
        assertTrue(isAiChatOrCompletion("/any/path", "custom-model-id"))

        // 系统控制面和遥测类路径
        assertFalse(isAiChatOrCompletion("/v1/models", null))
        assertFalse(isAiChatOrCompletion("/v1internal:recordCodeAssistMetrics", null))
        assertFalse(isAiChatOrCompletion("/v1internal:retrieveUserQuotasSummary", null))
        assertFalse(isAiChatOrCompletion("/v1internal:fetchUserInfo", null))
        assertFalse(isAiChatOrCompletion("/v1internal:listExperiments", null))
        assertFalse(isAiChatOrCompletion("/v1internal:cascadeNuxes", null))
        assertFalse(isAiChatOrCompletion("/v1internal:loadCodeAssist", null))
        assertFalse(isAiChatOrCompletion("/health", null))
    }
}
