package com.yuzhiqiang.antigravity.data.usage

import com.yuzhiqiang.antigravity.services.auth.RuntimeAccountProbe
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LanguageServerUsageReaderTest {

    @Test
    fun testPaginatesMetadataAndStepsAndDeduplicatesResponseId() = runBlocking {
        val endpoint = RuntimeAccountProbe.LanguageServerEndpoint(1234, "csrf")

        data class Request(val method: String, val body: String)

        val requests = mutableListOf<Request>()
        val reader = LanguageServerUsageReader(
            discoverEndpoints = { Result.success(listOf(endpoint)) },
            requestJson = { _, method, body, _ ->
                requests += Request(method, body)
                val page = requests.count { it.method == method } - 1
                if (method == "GetCascadeTrajectoryGeneratorMetadata") {
                    if (page == 0) {
                        """
                        {
                          "generatorMetadata": [
                            {
                              "chatModel": {
                                "responseModel": "vendor/model-a",
                                "displayName": "Model A",
                                "chatStartMetadata": {"createdAt": "2026-08-31T06:15:10Z"},
                                "usage": {
                                  "inputTokens": "10", "outputTokens": "7", "cacheReadTokens": "2",
                                  "cacheWriteTokens": "1", "reasoningTokens": "3", "responseId": "r1"
                                }
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    } else {
                        "{\"generatorMetadata\":[]}"
                    }
                } else if (page == 0) {
                    """
                    {
                      "steps": [
                        {
                          "metadata": {"createdAt": "2026-08-31T06:15:10Z"},
                          "modelUsage": {
                            "inputTokens": "10", "outputTokens": "7", "cacheReadTokens": "2",
                            "cacheWriteTokens": "1", "reasoningTokens": "3", "responseId": "r1"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                } else {
                    "{\"steps\":[]}"
                }
            }
        )

        val result = reader.read("conversation", "ide")

        assertNotNull(result)
        assertEquals(1, result.entries.size)
        val entry = result.entries.single()
        assertEquals(10L, entry.input)
        assertEquals("vendor/model-a", entry.modelObservation.responseModelId)
        assertEquals("Model A", entry.modelObservation.displayName)
        assertEquals(
            listOf(0, 1),
            requests
                .filter { it.method == "GetCascadeTrajectoryGeneratorMetadata" }
                .map { requestOffset(it.body) }
        )
        assertEquals(
            listOf(0, 1),
            requests
                .filter { it.method == "GetCascadeTrajectorySteps" }
                .map { requestOffset(it.body) }
        )
    }

    private fun requestOffset(body: String): Int {
        return Regex("""(?:generator_metadata_offset|step_offset)":(\d+)""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: -1
    }
}
