package com.yuzhiqiang.antigravity.data.usage

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExchangeRateServiceTest {

    @Test
    fun testDefaultRateAndCacheFallback() = runBlocking {
        val root = File.createTempFile("usage-exchange-", "-root").apply {
            delete()
            mkdirs()
        }
        try {
            val cacheFile = File(root, "exchange_rate_cache.json")
            cacheFile.writeText("""{"usdToCny":7.18,"updatedAt":${System.currentTimeMillis()}}""")

            val service = ExchangeRateService(customRootDir = root)
            assertEquals(7.18, service.currentUsdToCny(), 0.001)

            val rate = service.refreshRate(force = false)
            assertEquals(7.18, rate, 0.001)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testDefaultFallbackWhenNoCache() {
        val root = File.createTempFile("usage-exchange-empty-", "-root").apply {
            delete()
            mkdirs()
        }
        try {
            val service = ExchangeRateService(customRootDir = root)
            assertEquals(ExchangeRateService.DEFAULT_USD_TO_CNY, service.currentUsdToCny())
        } finally {
            root.deleteRecursively()
        }
    }
}
