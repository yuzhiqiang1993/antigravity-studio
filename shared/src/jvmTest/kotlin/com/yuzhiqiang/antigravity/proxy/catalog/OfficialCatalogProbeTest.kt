package com.yuzhiqiang.antigravity.proxy.catalog

import com.yuzhiqiang.antigravity.domain.model.ModelIdentityRegistryHolder
import com.yuzhiqiang.antigravity.domain.model.ModelIdentityStatus
import com.yuzhiqiang.antigravity.domain.model.ModelObservation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfficialCatalogProbeTest {

    @AfterTest
    fun clearCatalog() {
        OfficialCatalogProbe.clearRawOfficialCatalog()
    }

    @Test
    fun parsesOfficialIdentityCapabilitiesQuotaTagsTiersReplacementAndRoles() {
        val snapshot = OfficialCatalogProbe.parseOfficialCatalogSnapshot(catalogJson())
        val model = snapshot.models.first { it.catalogModelId == "catalog-opus" }

        assertEquals("MODEL_PLACEHOLDER_M77", model.runtimeModelId)
        assertEquals("claude-opus-4-6@20260201", model.providerModelId)
        assertEquals("claude-opus-4-6", model.canonicalModelId)
        assertEquals("claude-opus-4", model.baseModelId)
        assertEquals("20260201", model.version)
        assertEquals("API_PROVIDER_GOOGLE_GEMINI", model.catalogApiProvider)
        assertEquals("MODEL_PROVIDER_ANTHROPIC", model.providerVendor)
        assertEquals(-1, model.reasoningProfile?.budgetTokens)
        assertEquals(1024, model.reasoningProfile?.minBudgetTokens)
        assertEquals(0.42, model.quotaInfo?.remainingFraction)
        assertEquals("2026-09-02T00:00:00Z", model.quotaInfo?.resetTime)
        assertEquals("Preview", model.tags?.title)
        assertEquals("Limited rollout", model.tags?.description)
        assertEquals("catalog-sonnet", model.replacementCatalogModelId)
        assertTrue(model.tierGroupIds.contains("premium"))
        assertTrue(model.roles.containsAll(listOf("agent", "command", "tab")))
        assertEquals(listOf("catalog-opus", "catalog-sonnet"), snapshot.tierGroups["premium"])
        assertEquals("catalog-sonnet", snapshot.replacements.single().replacementCatalogModelId)
        assertEquals(
            "claude-sonnet-4-6",
            snapshot.models.first { it.catalogModelId == "catalog-sonnet" }.canonicalModelId
        )
    }

    @Test
    fun publishingCatalogRefreshesIdentityRegistry() {
        OfficialCatalogProbe.setRawOfficialCatalog(catalogJson())

        val resolved = ModelIdentityRegistryHolder.snapshot().resolve(
            ModelObservation(
                catalogModelId = "catalog-opus",
                runtimeModelId = "MODEL_PLACEHOLDER_M77",
                providerModelId = "claude-opus-4-6@20260201"
            )
        )

        assertEquals(ModelIdentityStatus.RESOLVED, resolved.status)
        assertEquals("catalog-opus", resolved.catalogModelId)
        assertEquals("claude-opus-4-6@20260201", resolved.providerModelId)
        assertNotNull(OfficialCatalogProbe.lastParsedSnapshot.models.singleOrNull {
            it.catalogModelId == "catalog-opus"
        })
    }

    @Test
    fun setRawOfficialCatalogEmitsFlowAndFiltersExcludedModelIds() {
        assertEquals(emptyList(), OfficialCatalogProbe.officialModelsFlow.value)

        OfficialCatalogProbe.setRawOfficialCatalog(catalogJson(), excludedModelIds = setOf("catalog-opus"))

        val emitted = OfficialCatalogProbe.officialModelsFlow.value
        assertEquals(1, emitted.size)
        assertEquals("catalog-sonnet", emitted.single().catalogModelId)

        OfficialCatalogProbe.clearRawOfficialCatalog()
        assertEquals(emptyList(), OfficialCatalogProbe.officialModelsFlow.value)
    }

    private fun catalogJson(): String =
        """
        {
          "response": {
            "models": {
              "catalog-opus": {
                "model": "MODEL_PLACEHOLDER_M77",
                "apiProvider": "API_PROVIDER_GOOGLE_GEMINI",
                "modelProvider": "MODEL_PROVIDER_ANTHROPIC",
                "vertexModelId": "claude-opus-4-6@20260201",
                "canonicalModelId": "claude-opus-4-6",
                "baseModelId": "claude-opus-4",
                "version": "20260201",
                "displayName": "Claude Opus Display Only",
                "thinkingBudget": -1,
                "minThinkingBudget": 1024,
                "supportsThinking": true,
                "quotaInfo": {
                  "remainingFraction": 0.42,
                  "resetTime": "2026-09-02T00:00:00Z"
                },
                "tag": {
                  "title": "Preview",
                  "description": "Limited rollout"
                },
                "replacement": {"modelId": "catalog-sonnet"},
                "roles": ["tab"]
              },
              "catalog-sonnet": {
                "model": "MODEL_PLACEHOLDER_M78",
                "vertexModelId": "claude-sonnet-4-6"
              }
            },
            "agentModelSorts": [{"groups": [{"modelIds": ["catalog-opus"]}]}],
            "defaultAgentModelId": "catalog-opus",
            "clientModelRoles": {"command": ["catalog-opus"]},
            "tieredModelIds": {"premium": ["catalog-opus", "catalog-sonnet"]}
          }
        }
        """.trimIndent()
}
