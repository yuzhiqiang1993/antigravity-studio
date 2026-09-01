package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.CompressionPolicyTargetType
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.domain.model.ModelIdentity
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.ModelRouteVariant
import com.yuzhiqiang.antigravity.domain.model.ModelVariantKind
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.domain.model.ProviderModelBinding
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport
import com.yuzhiqiang.antigravity.domain.model.ReasoningProfile
import com.yuzhiqiang.antigravity.proxy.routing.RouteResolver
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CustomCatalogBuilder {

    fun customCatalogEntries(
        config: AppConfig,
        includeTiered: Boolean = true,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null
    ): List<JsonObject> {
        val directEntries = config.modelRouteVariants
            .filter { variant -> variant.kind != ModelVariantKind.TIERED }
            .mapNotNull { variant ->
                val binding = bindingFor(config, variant) ?: return@mapNotNull null
                val provider = providerFor(config, binding) ?: return@mapNotNull null
                if (!RouteResolver.isRoutableModelRouteVariant(config, variant)) return@mapNotNull null
                buildCatalogEntry(
                    variant = variant,
                    binding = binding,
                    provider = provider,
                    policy = healedPolicy(
                        policyFor(config, variant, binding),
                        checkpointWorkers,
                        defaultCheckpointWorker
                    )
                )
            }
        if (!includeTiered) return directEntries
        return directEntries + tieredCatalogEntries(
            config,
            checkpointWorkers,
            defaultCheckpointWorker
        )
    }

    private fun tieredCatalogEntries(
        config: AppConfig,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): List<JsonObject> {
        val concrete = config.modelRouteVariants.filter { variant ->
            variant.kind != ModelVariantKind.TIERED &&
                    RouteResolver.isRoutableModelRouteVariant(config, variant)
        }
        val explicit = config.modelRouteVariants
            .filter { variant -> variant.kind == ModelVariantKind.TIERED && variant.enabled }
            .mapNotNull { tiered ->
                val member = preferredVariant(tiered.tierMemberVariantIds.mapNotNull { memberId ->
                    concrete.firstOrNull { variant -> variant.variantId == memberId }
                }) ?: return@mapNotNull null
                val binding = bindingFor(config, member) ?: return@mapNotNull null
                val provider = providerFor(config, binding) ?: return@mapNotNull null
                buildCatalogEntry(
                    variant = tiered,
                    binding = binding,
                    provider = provider,
                    policy = healedPolicy(
                        policyFor(config, tiered, binding),
                        checkpointWorkers,
                        defaultCheckpointWorker
                    ),
                    forceDynamicThinking = true
                )
            }
        val explicitFamilies = config.modelRouteVariants
            .filter { it.kind == ModelVariantKind.TIERED }
            .map(ModelIdentity::catalogFamilyBase)
            .toSet()
        val synthetic = concrete
            .filter { variant -> bindingFor(config, variant)?.capabilities?.reasoning?.supportsReasoning == true }
            .groupBy { variant -> variant.bindingId to ModelIdentity.catalogFamilyBase(variant) }
            .mapNotNull { (group, variants) ->
                val family = group.second
                if (family in explicitFamilies) return@mapNotNull null
                val preferred = preferredVariant(variants) ?: return@mapNotNull null
                val binding = bindingFor(config, preferred) ?: return@mapNotNull null
                val provider = providerFor(config, binding) ?: return@mapNotNull null
                buildCatalogEntry(
                    variant = preferred.copy(
                        variantId = "tiered:$family",
                        catalogModelId = "$family-tiered",
                        displayName = ModelIdentity.stripDisplayLevelSuffix(preferred.displayName),
                        kind = ModelVariantKind.TIERED,
                        reasoningProfile = null
                    ),
                    binding = binding,
                    provider = provider,
                    policy = healedPolicy(
                        policyFor(config, null, binding),
                        checkpointWorkers,
                        defaultCheckpointWorker
                    ),
                    forceDynamicThinking = true
                )
            }
        return explicit + synthetic
    }

    fun buildCatalogEntry(
        variant: ModelRouteVariant,
        binding: ProviderModelBinding,
        provider: Provider,
        policy: ModelCompressionPolicy?,
        forceDynamicThinking: Boolean = false
    ): JsonObject {
        val catalogModelId = ModelIdentity.catalogKey(variant)
        val runtimeModelId = ModelIdentity.effectiveRuntimeModelId(variant)
        val limits = binding.tokenLimits
        val contextWindow = limits.contextWindow ?: limits.inputTokenLimit
        val inputLimit = limits.inputTokenLimit ?: limits.contextWindow
        val outputLimit = limits.outputTokenLimit
        val resolvedPolicy = policy?.takeIf { it.enabled }?.resolveEffective(contextWindow, outputLimit)
        val capabilities = binding.capabilities
        return buildJsonObject {
            put("id", catalogModelId)
            put("catalogKey", catalogModelId)
            put("runtimeModelId", runtimeModelId)
            put("providerModelId", binding.providerModelId)
            put("variantId", variant.variantId)
            put("bindingId", binding.bindingId)
            put("name", "models/$runtimeModelId")
            put("model", runtimeModelId)
            put("planModel", runtimeModelId)
            put("requestedModel", runtimeModelId)
            put("displayName", variant.displayName.ifBlank { binding.effectiveName })
            put("description", "Custom BYOK Model (Provider: ${provider.name})")
            put("apiProvider", "API_PROVIDER_GOOGLE_GEMINI")
            put("modelProvider", modelProvider(provider.protocol))
            put("recommended", false)
            contextWindow?.let { put("contextWindow", it) }
            inputLimit?.let { put("inputTokenLimit", it); put("maxTokens", it) }
            outputLimit?.let { put("outputTokenLimit", it); put("maxOutputTokens", it) }
            put("supportsImages", ModelModality.IMAGE in capabilities.inputModalities)
            put("supportsAudio", ModelModality.AUDIO in capabilities.inputModalities)
            put("supportsVideo", ModelModality.VIDEO in capabilities.inputModalities)
            put("supportsTools", capabilities.tools)
            put("supportsThinking", capabilities.reasoning.supportsReasoning)
            put("roles", buildJsonArray {
                capabilities.roles.forEach { role -> add(JsonPrimitive(role.name.lowercase())) }
            })
            put("inputModalities", buildJsonArray {
                capabilities.inputModalities.forEach { modality -> add(JsonPrimitive(modality.name.lowercase())) }
            })
            put("outputModalities", buildJsonArray {
                capabilities.outputModalities.forEach { modality -> add(JsonPrimitive(modality.name.lowercase())) }
            })
            put("supportedMimeTypes", buildJsonObject {
                capabilities.inputMimeTypes.forEach { mime -> put(mime, true) }
            })
            if (capabilities.reasoning.supportsReasoning) {
                put(
                    "thinkingBudget",
                    if (forceDynamicThinking) -1 else effectiveThinkingBudget(binding, variant.reasoningProfile)
                )
                (variant.reasoningProfile?.minBudgetTokens ?: capabilities.reasoning.minThinkingBudget)
                    ?.let { put("minThinkingBudget", it) }
            }
            put("supportedGenerationMethods", buildJsonArray {
                add(JsonPrimitive("generateContent"))
                add(JsonPrimitive("streamGenerateContent"))
            })
            provider.name.trim().takeIf(String::isNotEmpty)?.let { name ->
                put("tagTitle", name)
                put("tagDescription", "BYOK")
            }
            resolvedPolicy?.let { put("modelExperiments", CatalogCompressionApplier.checkpointExperiments(it)) }
        }
    }

    private fun effectiveThinkingBudget(
        binding: ProviderModelBinding,
        profile: ReasoningProfile?
    ): Int {
        profile?.budgetTokens?.let { return it }
        profile?.mapping?.let(ReasoningMappingSupport::mappingValueAsInt)?.let { return it }
        val levelMapping = profile?.level?.let { level ->
            ReasoningMappingSupport.parse(binding.capabilities.reasoning.levels)[level]
        }
        return levelMapping?.let(ReasoningMappingSupport::mappingValueAsInt)
            ?: binding.capabilities.reasoning.thinkingBudget
            ?: -1
    }

    private fun policyFor(
        config: AppConfig,
        variant: ModelRouteVariant?,
        binding: ProviderModelBinding
    ): ModelCompressionPolicy? {
        val variantPolicy = variant?.let { routeVariant ->
            config.compressionPolicyAssignments.firstOrNull { assignment ->
                assignment.targetType == CompressionPolicyTargetType.MODEL_ROUTE_VARIANT &&
                        assignment.targetId == routeVariant.variantId
            }?.policy
        }
        val bindingPolicy = config.compressionPolicyAssignments.firstOrNull { assignment ->
            assignment.targetType == CompressionPolicyTargetType.PROVIDER_MODEL_BINDING &&
                    assignment.targetId == binding.bindingId
        }?.policy
        return variantPolicy ?: bindingPolicy ?: binding.compressionPolicy
    }

    private fun healedPolicy(
        policy: ModelCompressionPolicy?,
        checkpointWorkers: Collection<String>,
        defaultCheckpointWorker: String?
    ): ModelCompressionPolicy? = CatalogCompressionApplier.healCheckpointPolicy(
        policy,
        checkpointWorkers,
        defaultCheckpointWorker
    )

    private fun bindingFor(config: AppConfig, variant: ModelRouteVariant): ProviderModelBinding? {
        val bindingId = variant.bindingId ?: return null
        return config.providerModelBindings.firstOrNull { binding -> binding.bindingId == bindingId }
    }

    private fun providerFor(config: AppConfig, binding: ProviderModelBinding): Provider? =
        config.providers.firstOrNull { provider -> provider.id == binding.providerConfigId && provider.enabled }

    private fun preferredVariant(variants: List<ModelRouteVariant>): ModelRouteVariant? {
        return ModelIdentity.REASONING_LEVEL_PRIORITY.firstNotNullOfOrNull { level ->
            variants.firstOrNull { variant -> variant.reasoningProfile?.level == level }
        } ?: variants.firstOrNull()
    }

    fun modelProvider(protocol: ProviderProtocol): String = when (protocol) {
        ProviderProtocol.ANTHROPIC_MESSAGES -> "MODEL_PROVIDER_ANTHROPIC"
        ProviderProtocol.GEMINI_GENERATE_CONTENT -> "MODEL_PROVIDER_GOOGLE"
        ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
        ProviderProtocol.OPENAI_RESPONSES -> "MODEL_PROVIDER_OPENAI"
    }
}
