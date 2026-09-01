package com.yuzhiqiang.antigravity.domain.model

/**
 * 将 Provider 编辑器选中的模型 Binding 同步为可被宿主发现的 Route Variant 图。
 *
 * Binding、目录 ID、Runtime slot 与 reasoning variant 各自保持稳定，显示名称变化
 * 不会生成新的身份。
 */
object ProviderModelSynchronizer {
    data class SyncResult(
        val providerModelBindings: List<ProviderModelBinding>,
        val modelRouteVariants: List<ModelRouteVariant>
    )

    fun synchronize(
        config: AppConfig,
        provider: Provider,
        selectedBindings: List<ProviderModelBinding>
    ): Result<SyncResult> {
        val currentBindings = config.providerModelBindings.filter { binding ->
            binding.providerConfigId == provider.id
        }
        val currentBindingIds = currentBindings.map(ProviderModelBinding::bindingId).toSet()
        val retainedVariants = config.modelRouteVariants.filterNot { variant ->
            variant.bindingId in currentBindingIds
        }
        val occupiedRuntimeIds = collectOccupiedRuntimeIds(config, currentBindingIds)
        val occupiedVariantIds = config.modelRouteVariants.mapTo(mutableSetOf(), ModelRouteVariant::variantId)
        val occupiedCatalogIds = config.modelRouteVariants.mapTo(mutableSetOf(), ModelRouteVariant::catalogModelId)
        val selectedByProviderModelId = selectedBindings.distinctBy(ProviderModelBinding::providerModelId)
        reserveSelectedExistingRuntimeIds(
            config = config,
            currentBindings = currentBindings,
            selectedProviderModelIds = selectedByProviderModelId.map(ProviderModelBinding::providerModelId).toSet(),
            occupiedRuntimeIds = occupiedRuntimeIds
        )
        val synchronizedBindings = mutableListOf<ProviderModelBinding>()
        val synchronizedVariants = retainedVariants.toMutableList()

        for (selectedBinding in selectedByProviderModelId) {
            val existingBinding = currentBindings.firstOrNull { binding ->
                binding.providerModelId == selectedBinding.providerModelId
            }
            val existingVariants = existingBinding?.let { binding ->
                config.modelRouteVariants.filter { variant -> variant.bindingId == binding.bindingId }
            }.orEmpty()
            releaseExistingRuntimeIds(existingVariants, occupiedRuntimeIds)
            val primaryRuntimeId = if (existingVariants.isEmpty()) {
                ModelIdentity.resolveRuntimeModelId(
                    seed = "${provider.id}\u0000${selectedBinding.providerModelId}",
                    configuredId = null,
                    occupiedIds = occupiedRuntimeIds
                ).getOrElse { error -> return Result.failure(error) }
            } else {
                null
            }
            val synchronizedBinding = selectedBinding.copy(
                bindingId = existingBinding?.bindingId ?: selectedBinding.bindingId,
                providerConfigId = provider.id
            )
            synchronizedBindings += synchronizedBinding
            synchronizedVariants += synchronizeVariants(
                provider = provider,
                binding = synchronizedBinding,
                existingVariants = existingVariants,
                primaryRuntimeId = primaryRuntimeId,
                occupiedVariantIds = occupiedVariantIds,
                occupiedCatalogIds = occupiedCatalogIds,
                occupiedRuntimeIds = occupiedRuntimeIds
            )
        }

        return Result.success(
            SyncResult(
                providerModelBindings = config.providerModelBindings
                    .filterNot { binding -> binding.providerConfigId == provider.id } + synchronizedBindings,
                modelRouteVariants = synchronizedVariants
            )
        )
    }

    private fun collectOccupiedRuntimeIds(
        config: AppConfig,
        currentBindingIds: Set<String>
    ): MutableSet<String> = config.modelRouteVariants
        .filterNot { variant -> variant.bindingId in currentBindingIds }
        .mapTo(mutableSetOf(), ModelIdentity::effectiveRuntimeModelId)

    private fun synchronizeVariants(
        provider: Provider,
        binding: ProviderModelBinding,
        existingVariants: List<ModelRouteVariant>,
        primaryRuntimeId: String?,
        occupiedVariantIds: MutableSet<String>,
        occupiedCatalogIds: MutableSet<String>,
        occupiedRuntimeIds: MutableSet<String>
    ): List<ModelRouteVariant> {
        val desiredLevels = desiredReasoningLevels(provider, binding, existingVariants)
        if (existingVariants.isNotEmpty()) {
            return synchronizeExistingVariants(
                binding = binding,
                existingVariants = existingVariants,
                desiredLevels = desiredLevels,
                occupiedVariantIds = occupiedVariantIds,
                occupiedCatalogIds = occupiedCatalogIds,
                occupiedRuntimeIds = occupiedRuntimeIds
            )
        }
        return desiredLevels.mapIndexed { index, level ->
            createVariant(
                binding = binding,
                reasoningLevel = level,
                occupiedVariantIds = occupiedVariantIds,
                occupiedCatalogIds = occupiedCatalogIds,
                occupiedRuntimeIds = occupiedRuntimeIds,
                preferredRuntimeId = if (index == 0) requireNotNull(primaryRuntimeId) else null
            )
        }
    }

    private fun synchronizeExistingVariants(
        binding: ProviderModelBinding,
        existingVariants: List<ModelRouteVariant>,
        desiredLevels: List<ReasoningLevel?>,
        occupiedVariantIds: MutableSet<String>,
        occupiedCatalogIds: MutableSet<String>,
        occupiedRuntimeIds: MutableSet<String>
    ): List<ModelRouteVariant> {
        val desiredLevelSet = desiredLevels.toSet()
        val retained = existingVariants.filter { variant -> variant.reasoningProfile?.level in desiredLevelSet }
        val synchronized = retained.map { variant ->
            val runtimeId = ModelIdentity.resolveRuntimeModelId(
                seed = "${binding.bindingId}\u0000${variant.variantId}",
                configuredId = ModelIdentity.effectiveRuntimeModelId(variant),
                occupiedIds = occupiedRuntimeIds
            ).getOrThrow()
            occupiedRuntimeIds += runtimeId
            variant.copy(
                bindingId = binding.bindingId,
                runtimeModelId = runtimeId,
                displayName = reasoningDisplayName(binding, variant.reasoningProfile?.level),
                kind = kindFor(variant.reasoningProfile?.level),
                enabled = binding.enabled
            )
        }.toMutableList()

        val existingLevels = retained.map { variant -> variant.reasoningProfile?.level }.toSet()
        desiredLevels.filterNot(existingLevels::contains).forEach { level ->
            synchronized += createVariant(
                binding = binding,
                reasoningLevel = level,
                occupiedVariantIds = occupiedVariantIds,
                occupiedCatalogIds = occupiedCatalogIds,
                occupiedRuntimeIds = occupiedRuntimeIds
            )
        }
        return synchronized
    }

    private fun createVariant(
        binding: ProviderModelBinding,
        reasoningLevel: ReasoningLevel?,
        occupiedVariantIds: MutableSet<String>,
        occupiedCatalogIds: MutableSet<String>,
        occupiedRuntimeIds: MutableSet<String>,
        preferredRuntimeId: String? = null
    ): ModelRouteVariant {
        val variantId = ModelIdentity.createModelRouteVariantId(binding, occupiedVariantIds, reasoningLevel)
        val catalogModelId = ModelIdentity.createCatalogModelId(binding, occupiedCatalogIds, reasoningLevel)
        val runtimeModelId = preferredRuntimeId?.takeUnless(String::isBlank)
            ?: ModelIdentity.allocateRuntimeModelId(occupiedRuntimeIds).getOrThrow()
        occupiedRuntimeIds += runtimeModelId
        return ModelRouteVariant(
            variantId = variantId,
            bindingId = binding.bindingId,
            catalogModelId = catalogModelId,
            runtimeModelId = runtimeModelId,
            displayName = reasoningDisplayName(binding, reasoningLevel),
            kind = kindFor(reasoningLevel),
            reasoningProfile = reasoningLevel?.let { level ->
                ReasoningProfile(
                    level = level,
                    source = ModelIdentitySource.PROVIDER_CATALOG
                )
            },
            enabled = binding.enabled
        )
    }

    private fun reasoningDisplayName(binding: ProviderModelBinding, level: ReasoningLevel?): String {
        val baseName = binding.displayName.ifBlank { binding.providerModelId }
        return level?.let { "$baseName (${it.label})" } ?: baseName
    }

    private fun kindFor(level: ReasoningLevel?): ModelVariantKind =
        if (level == null) ModelVariantKind.DIRECT else ModelVariantKind.REASONING_VARIANT

    private fun desiredReasoningLevels(
        provider: Provider,
        binding: ProviderModelBinding,
        existingVariants: List<ModelRouteVariant>
    ): List<ReasoningLevel?> {
        val reasoning = binding.capabilities.reasoning
        val rawConfiguredLevels = ReasoningMappingSupport.configuredLevels(reasoning.levels)
        val configuredLevels = rawConfiguredLevels.filterNot { level -> level == ReasoningLevel.OFF }
        if (configuredLevels.isNotEmpty()) return configuredLevels
        if (rawConfiguredLevels.isNotEmpty()) return listOf(null)
        if (reasoning.thinkingBudget != null || reasoning.minThinkingBudget != null) return listOf(null)
        if (reasoning.supported == false) return listOf(null)
        if (existingVariants.isNotEmpty()) {
            return existingVariants.map { variant -> variant.reasoningProfile?.level }.distinct()
        }
        if (reasoning.supported == null && reasoning.levels == null) return listOf(null)
        if (reasoning.supported != true && reasoning.levels != null) return listOf(null)
        return ReasoningMappingSupport.defaultLevels(provider.protocol)
    }

    private fun reserveSelectedExistingRuntimeIds(
        config: AppConfig,
        currentBindings: List<ProviderModelBinding>,
        selectedProviderModelIds: Set<String>,
        occupiedRuntimeIds: MutableSet<String>
    ) {
        currentBindings
            .filter { binding -> binding.providerModelId in selectedProviderModelIds }
            .forEach { binding ->
                config.modelRouteVariants
                    .filter { variant -> variant.bindingId == binding.bindingId }
                    .mapTo(occupiedRuntimeIds, ModelIdentity::effectiveRuntimeModelId)
            }
    }

    private fun releaseExistingRuntimeIds(
        variants: List<ModelRouteVariant>,
        occupiedRuntimeIds: MutableSet<String>
    ) {
        variants.forEach { variant -> occupiedRuntimeIds.remove(ModelIdentity.effectiveRuntimeModelId(variant)) }
    }
}
