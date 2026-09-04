package com.yuzhiqiang.antigravity.doctor

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckCategory
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoctorEngineTest {

    @Test
    fun testDiagnoseParallelExecutionAndResultCompleteness() = runBlocking {
        val tempDir = File.createTempFile("doctor_engine_test_", "_dir").apply {
            delete()
            mkdirs()
        }
        try {
            val configStore = ConfigStore(customRootDir = tempDir)
            val proxyServer = LocalProxyServer(configStore)
            val doctorEngine = DoctorEngine(configStore, proxyServer)

            val report = doctorEngine.diagnose()
            assertNotNull(report)
            assertTrue(report.items.isNotEmpty(), "Diagnose report should contain check items")

            // 验证核心分类均有产出且被正确并行汇总
            val categories = report.items.map { it.category }.toSet()
            assertTrue(categories.contains(DoctorCheckCategory.PROXY), "Should contain PROXY check")
            assertTrue(categories.contains(DoctorCheckCategory.CONFIG), "Should contain CONFIG check")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
