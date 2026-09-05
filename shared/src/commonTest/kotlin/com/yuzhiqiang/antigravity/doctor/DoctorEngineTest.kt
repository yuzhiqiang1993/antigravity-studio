package com.yuzhiqiang.antigravity.doctor

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckCategory
import com.yuzhiqiang.antigravity.host.HostTestEnvironment
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoctorEngineTest {

    @Test
    fun testDiagnoseParallelExecutionAndResultCompleteness() = runBlocking {
        HostTestEnvironment().use { environment ->
            val cliExecutable = environment.root.resolve("agy").apply {
                writeText("#!/bin/sh\nprintf '1.0.0\\n'\n")
                setExecutable(true)
            }
            val configStore = ConfigStore(customRootDir = environment.root.resolve("config"))
            configStore.updateConfig { config ->
                config.copy(customHostPaths = listOf("ide", "app", "cli").associateWith {
                    if (it == "cli") cliExecutable.absolutePath
                    else environment.root.resolve("empty-$it").apply { mkdirs() }.absolutePath
                })
            }
            val proxyServer = LocalProxyServer(configStore)
            val doctorEngine = DoctorEngine(configStore, proxyServer)

            val report = doctorEngine.diagnose()
            assertNotNull(report)
            assertTrue(report.items.isNotEmpty(), "Diagnose report should contain check items")

            // 验证核心分类均有产出且被正确并行汇总
            val categories = report.items.map { it.category }.toSet()
            assertTrue(categories.contains(DoctorCheckCategory.PROXY), "Should contain PROXY check")
            assertTrue(categories.contains(DoctorCheckCategory.CONFIG), "Should contain CONFIG check")
            assertEquals(0, environment.environmentWrites)
            assertEquals(0, environment.environmentClears)
        }
    }
}
