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

    @Test
    fun testReadStepsDoesNotRefetchMetadata() = runBlocking {
        val endpoint = RuntimeAccountProbe.LanguageServerEndpoint(1234, "csrf")
        val requestedMethods = mutableListOf<String>()
        var page = 0
        val reader = LanguageServerUsageReader(
            discoverEndpoints = { Result.success(listOf(endpoint)) },
            requestJson = { _, method, _, _ ->
                requestedMethods += method
                check(method == "GetCascadeTrajectorySteps")
                if (page++ == 0) {
                    """
                    {
                      "steps": [
                        {
                          "metadata": {"createdAt": "2026-08-31T06:15:10Z"},
                          "modelUsage": {
                            "inputTokens": "20", "outputTokens": "5", "responseId": "steps-only"
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

        val result = reader.readSteps("conversation", "ide")

        assertNotNull(result)
        assertEquals(listOf("steps-only"), result.entries.map { it.responseId })
        assertEquals(
            listOf("GetCascadeTrajectorySteps", "GetCascadeTrajectorySteps"),
            requestedMethods
        )
    }

    @Test
    fun testReadStepsContinuesUntilRequiredResponseIdsAreCovered() = runBlocking {
        val firstEndpoint = RuntimeAccountProbe.LanguageServerEndpoint(1234, "first")
        val secondEndpoint = RuntimeAccountProbe.LanguageServerEndpoint(5678, "second")
        val pagesByPort = mutableMapOf<Int, Int>()
        val reader = LanguageServerUsageReader(
            discoverEndpoints = { Result.success(listOf(firstEndpoint, secondEndpoint)) },
            requestJson = { endpoint, method, _, _ ->
                check(method == "GetCascadeTrajectorySteps")
                val page = pagesByPort.getOrDefault(endpoint.port, 0)
                pagesByPort[endpoint.port] = page + 1
                if (page > 0) {
                    "{\"steps\":[]}"
                } else {
                    val responseId = if (endpoint.port == firstEndpoint.port) "unrelated" else "required"
                    """
                    {
                      "steps": [
                        {"modelUsage": {"inputTokens": "1", "responseId": "$responseId"}}
                      ]
                    }
                    """.trimIndent()
                }
            }
        )

        val result = reader.readSteps("conversation", "ide", setOf("required"))

        assertNotNull(result)
        assertEquals(true, result.complete)
        assertEquals(setOf("unrelated", "required"), result.entries.map { it.responseId }.toSet())
        assertEquals(2, pagesByPort[firstEndpoint.port])
        assertEquals(2, pagesByPort[secondEndpoint.port])
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
