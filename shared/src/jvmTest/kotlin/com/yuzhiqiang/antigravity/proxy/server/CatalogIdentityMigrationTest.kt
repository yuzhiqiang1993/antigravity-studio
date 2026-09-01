package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicyAssignment
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.ModelTokenLimits
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogIdentityMigrationTest {

    @Test
    fun officialFilterUsesOnlyCatalogKeyAndDoesNotExpandReplacementAliases() {
        val root = officialCatalog()

        val displayOnly = CatalogInjector.removeDisabledOfficialModels(root, listOf("Shared Display"))
        val displayOnlyModels = displayOnly["models"]!!.jsonObject
        assertTrue("catalog-high" in displayOnlyModels)
        assertTrue("catalog-low" in displayOnlyModels)

        val keyDisabled = CatalogInjector.removeDisabledOfficialModels(root, listOf("catalog-high"))
        val keyDisabledModels = keyDisabled["models"]!!.jsonObject
        assertFalse("catalog-high" in keyDisabledModels)
        assertTrue("catalog-low" in keyDisabledModels)
    }

    @Test
    fun officialCompressionAssignmentMatchesExactCatalogTargetOnly() {
        val assignment = ModelCompressionPolicyAssignment(
            targetType = CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL,
            targetId = "catalog-high",
            policy = ModelCompressionPolicy()
        )

        val result = CatalogInjector.applyOfficialCompressionPolicies(
            officialCatalog(),
            listOf(assignment)
        )["models"]!!.jsonObject

        assertNotNull(result["catalog-high"]!!.jsonObject["modelExperiments"])
        assertFalse("modelExperiments" in result["catalog-low"]!!.jsonObject)
    }

    @Test
    fun byokVariantAssignmentDoesNotLeakToSiblingVariant() {
        val provider = Provider(id = "provider-1", name = "Provider")
        val binding = ProviderModelBinding(
            bindingId = "binding-1",
            providerConfigId = provider.id,
            providerModelId = "provider/model",
            displayName = "Provider Model",
            tokenLimits = ModelTokenLimits(
                contextWindow = 128_000,
                inputTokenLimit = 128_000,
                outputTokenLimit = 16_384
            )
        )
        val high = ModelRouteVariant(
            variantId = "variant-high",
            bindingId = binding.bindingId,
            catalogModelId = "byok-high",
            runtimeModelId = "MODEL_PLACEHOLDER_M400",
            displayName = "Same Display"
        )
        val low = high.copy(
            variantId = "variant-low",
            catalogModelId = "byok-low",
            runtimeModelId = "MODEL_PLACEHOLDER_M401"
        )
        val config = AppConfig(
            providers = listOf(provider),
            providerModelBindings = listOf(binding),
            modelRouteVariants = listOf(high, low),
            compressionPolicyAssignments = listOf(
                ModelCompressionPolicyAssignment(
                    targetType = CompressionPolicyTargetType.MODEL_ROUTE_VARIANT,
                    targetId = high.variantId,
                    policy = ModelCompressionPolicy()
                )
            )
        )

        val models = CatalogInjector.injectCustomModels(
            buildJsonObject { put("models", buildJsonObject {}) },
            config,
            includeTiered = false
        )["models"]!!.jsonObject

        assertNotNull(models["byok-high"]!!.jsonObject["modelExperiments"])
        assertFalse("modelExperiments" in models["byok-low"]!!.jsonObject)
    }

    @Test
    fun displayAndReasoningSuffixDoNotSelectCompressionPolicy() {
        val assignments = listOf(
            ModelCompressionPolicyAssignment(
                targetType = CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL,
                targetId = "catalog",
                policy = ModelCompressionPolicy()
            ),
            ModelCompressionPolicyAssignment(
                targetType = CompressionPolicyTargetType.OFFICIAL_CATALOG_MODEL,
                targetId = "Shared Display",
                policy = ModelCompressionPolicy()
            )
        )

        val result = CatalogInjector.applyOfficialCompressionPolicies(
            officialCatalog(),
            assignments
        )["models"]!!.jsonObject

        assertFalse("modelExperiments" in result["catalog-high"]!!.jsonObject)
        assertFalse("modelExperiments" in result["catalog-low"]!!.jsonObject)
    }

    private fun officialCatalog(): JsonObject = buildJsonObject {
        put("models", buildJsonObject {
            put("catalog-high", catalogEntry("runtime-high"))
            put("catalog-low", catalogEntry("runtime-low"))
        })
        put("deprecatedModelIds", buildJsonObject {
            put("catalog-high", buildJsonObject {
                put("newModelId", JsonPrimitive("catalog-low"))
            })
        })
        put("agentModelSorts", buildJsonArray {
            add(buildJsonObject {
                put("groups", buildJsonArray {
                    add(buildJsonObject {
                        put("modelIds", buildJsonArray {
                            add(JsonPrimitive("catalog-high"))
                            add(JsonPrimitive("catalog-low"))
                        })
                    })
                })
            })
        })
    }

    private fun catalogEntry(runtimeModelId: String): JsonObject = buildJsonObject {
        put("displayName", JsonPrimitive("Shared Display"))
        put("model", JsonPrimitive(runtimeModelId))
        put("contextWindow", JsonPrimitive(128_000))
        put("inputTokenLimit", JsonPrimitive(128_000))
        put("outputTokenLimit", JsonPrimitive(16_384))
    }
}
