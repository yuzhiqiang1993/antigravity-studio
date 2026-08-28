package com.yuzhiqiang.antigravity.proxy

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
            modelId = "custom-gpt-5",
            requestedModelId = "gpt-5",
            clientSource = "Antigravity IDE",
            providerName = "OpenAI",
            isOfficialPassthrough = false,
            timestamp = 1000L
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
            errorMessage = "upstream failed",
            errorSource = "UPSTREAM_RESPONSE",
            usage = NeutralUsage(inputTokens = 100, outputTokens = 200, totalTokens = 300),
            retryCount = 2
        )
        val finishedLogs = ActivityRecorder.logs.value
        assertEquals(1, finishedLogs.size)
        val finishedLog = finishedLogs.first()
        assertFalse(finishedLog.isPending)
        assertEquals(200, finishedLog.statusCode)
        assertEquals(3500L, finishedLog.durationMs)
        assertEquals(1500L, finishedLog.firstTokenMs)
        assertEquals(300L, finishedLog.totalTokens)
        assertEquals("UPSTREAM_RESPONSE", finishedLog.errorSource)
        assertEquals(2, finishedLog.retryCount)
    }

    @Test
    fun testRecordDirectly() {
        ActivityRecorder.record(
            method = "GET",
            path = "/v1beta/models",
            modelId = null,
            providerName = "Studio Local Catalog",
            statusCode = 200,
            durationMs = 50L,
            isOfficialPassthrough = false
        )
        val logs = ActivityRecorder.logs.value
        assertEquals(1, logs.size)
        assertFalse(logs.first().isPending)
        assertEquals(200, logs.first().statusCode)
    }
}
