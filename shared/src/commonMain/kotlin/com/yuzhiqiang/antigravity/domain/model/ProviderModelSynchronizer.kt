package com.yuzhiqiang.antigravity.domain.model


/**
 * 将 Provider 编辑器选中的上游模型同步为可被宿主发现的模型图。
 *
 * Provider 保存同时维护 UpstreamModel 与 VirtualModel，确保新增模型拥有稳定
 * Host Model ID，已有模型和 reasoning variant 不因 UI 再次保存而被无故替换。
 */
object ProviderModelSynchronizer {
    data class SyncResult(
        val upstreamModels: List<UpstreamModel>,
        val virtualModels: List<VirtualModel>
    )

    /** 同步指定 Provider 的上游模型与虚拟模型。 */
    fun synchronize(
        config: AppConfig,
        provider: Provider,
        selectedModels: List<UpstreamModel>
    ): Result<SyncResult> {
        val currentUpstreams = config.upstreamModels.filter { upstream ->
            upstream.providerId == provider.id
        }
        val currentUpstreamIds = currentUpstreams.map { upstream -> upstream.id }.toSet()
        val retainedVirtuals = config.virtualModels.filterNot { virtual ->
            virtual.upstreamModelId in currentUpstreamIds
        }
        val occupiedHostIds = collectOccupiedHostIds(config, currentUpstreamIds)
        val occupiedVirtualIds = config.virtualModels.map { virtual -> virtual.id }.toMutableSet()
        val selectedByUpstreamId = selectedModels.distinctBy { model -> model.upstreamModelId }
        reserveSelectedExistingIds(
            config = config,
            currentUpstreams = currentUpstreams,
            selectedUpstreamIds = selectedByUpstreamId.map { model -> model.upstreamModelId }.toSet(),
            occupiedHostIds = occupiedHostIds
        )
        val synchronizedUpstreams = mutableListOf<UpstreamModel>()
        val synchronizedVirtuals = retainedVirtuals.toMutableList()

        for (selectedModel in selectedByUpstreamId) {
            val existingUpstream = currentUpstreams.firstOrNull { upstream ->
                upstream.upstreamModelId == selectedModel.upstreamModelId
            }
            val existingVirtuals = existingUpstream?.let { upstream ->
                config.virtualModels.filter { virtual -> virtual.upstreamModelId == upstream.id }
            }.orEmpty()
            releaseExistingHostIds(existingVirtuals, occupiedHostIds)
            val primaryHostId = if (existingVirtuals.isEmpty()) {
                val hostIdResult = ModelIdentity.resolveHostModelId(
                    seed = "${provider.id}:${selectedModel.upstreamModelId}",
                    configuredId = null,
                    occupiedIds = occupiedHostIds
                )
                if (hostIdResult.isFailure) {
                    return Result.failure(
                        hostIdResult.exceptionOrNull() ?: IllegalStateException("无法分配模型 Host Model ID")
                    )
                }
                hostIdResult.getOrThrow()
            } else {
                null
            }
            val synchronizedUpstream = selectedModel.copy(
                id = existingUpstream?.id ?: selectedModel.id,
                providerId = provider.id
            )
            synchronizedUpstreams += synchronizedUpstream
            synchronizedVirtuals += synchronizeVirtuals(
                provider = provider,
                upstream = synchronizedUpstream,
                existingVirtuals = existingVirtuals,
                primaryHostId = primaryHostId,
                occupiedVirtualIds = occupiedVirtualIds,
                occupiedHostIds = occupiedHostIds
            )
        }

        return Result.success(
            SyncResult(
                upstreamModels = config.upstreamModels
                    .filterNot { upstream -> upstream.providerId == provider.id } + synchronizedUpstreams,
                virtualModels = synchronizedVirtuals
            )
        )
    }

    private fun collectOccupiedHostIds(
        config: AppConfig,
        currentUpstreamIds: Set<String>
    ): MutableSet<String> {
        return config.virtualModels
            .filterNot { virtual -> virtual.upstreamModelId in currentUpstreamIds }
            .mapTo(mutableSetOf(), ModelIdentity::effectiveHostModelId)
    }

    private fun synchronizeVirtuals(
        provider: Provider,
        upstream: UpstreamModel,
        existingVirtuals: List<VirtualModel>,
        primaryHostId: String?,
        occupiedVirtualIds: MutableSet<String>,
        occupiedHostIds: MutableSet<String>
    ): List<VirtualModel> {
        val desiredLevels = desiredReasoningLevels(provider, upstream, existingVirtuals)
        if (existingVirtuals.isNotEmpty()) {
            return synchronizeExistingVirtuals(
                upstream = upstream,
                existingVirtuals = existingVirtuals,
                desiredLevels = desiredLevels,
                occupiedVirtualIds = occupiedVirtualIds,
                occupiedHostIds = occupiedHostIds
            )
        }

        return desiredLevels.mapIndexed { index, level ->
            createVirtualModel(
                upstream = upstream,
                reasoningLevel = level,
                occupiedVirtualIds = occupiedVirtualIds,
                occupiedHostIds = occupiedHostIds,
                preferredHostId = if (index == 0) requireNotNull(primaryHostId) else null
            )
        }
    }

    private fun synchronizeExistingVirtuals(
        upstream: UpstreamModel,
        existingVirtuals: List<VirtualModel>,
        desiredLevels: List<ReasoningLevel?>,
        occupiedVirtualIds: MutableSet<String>,
        occupiedHostIds: MutableSet<String>
    ): List<VirtualModel> {
        val desiredLevelSet = desiredLevels.toSet()
        val retainedVirtuals = existingVirtuals.filter { virtual ->
            virtual.defaultReasoningLevel in desiredLevelSet
        }
        val synchronized = retainedVirtuals.map { virtual ->
            val hostId = ModelIdentity.resolveHostModelId(
                seed = "${upstream.id}:${virtual.id}",
                configuredId = ModelIdentity.effectiveHostModelId(virtual),
                occupiedIds = occupiedHostIds
            ).getOrThrow()
            occupiedHostIds += hostId
            virtual.copy(
                upstreamModelId = upstream.id,
                hostModelId = hostId,
                capabilities = upstream.capabilities
            )
        }.toMutableList()

        val existingLevels = retainedVirtuals.map { virtual -> virtual.defaultReasoningLevel }.toSet()
        desiredLevels
            .filterNot { level -> level in existingLevels }
            .forEach { level ->
                synchronized += createVirtualModel(
                    upstream = upstream,
                    reasoningLevel = level,
                    occupiedVirtualIds = occupiedVirtualIds,
                    occupiedHostIds = occupiedHostIds
                )
            }
        return synchronized
    }

    private fun createVirtualModel(
        upstream: UpstreamModel,
        reasoningLevel: ReasoningLevel?,
        occupiedVirtualIds: MutableSet<String>,
        occupiedHostIds: MutableSet<String>,
        preferredHostId: String? = null
    ): VirtualModel {
        val virtualId = ModelIdentity.createVirtualModelId(upstream, occupiedVirtualIds, reasoningLevel)
        val hostId = preferredHostId?.takeUnless { it.isBlank() }
            ?: ModelIdentity.allocateHostModelId(occupiedHostIds).getOrThrow()
        occupiedHostIds += hostId
        return VirtualModel(
            id = virtualId,
            name = upstream.name,
            displayName = reasoningDisplayName(upstream, reasoningLevel),
            upstreamModelId = upstream.id,
            hostModelId = hostId,
            capabilities = upstream.capabilities,
            defaultReasoningLevel = reasoningLevel,
            enabled = upstream.enabled
        )
    }

    private fun reasoningDisplayName(upstream: UpstreamModel, level: ReasoningLevel?): String? {
        val baseName = upstream.displayName?.takeIf { it.isNotBlank() } ?: upstream.name
        val levelName = level?.let {
            when (it) {
                ReasoningLevel.OFF -> "Off"
                ReasoningLevel.LOW -> "Low"
                ReasoningLevel.MEDIUM -> "Medium"
                ReasoningLevel.HIGH -> "High"
                ReasoningLevel.X_HIGH -> "X-High"
                ReasoningLevel.MAX -> "Max"
                ReasoningLevel.ADAPTIVE -> "Adaptive"
                ReasoningLevel.AUTO -> "Custom"
            }
        }
        return levelName?.let { "$baseName ($it)" } ?: baseName
    }

    private fun desiredReasoningLevels(
        provider: Provider,
        upstream: UpstreamModel,
        existingVirtuals: List<VirtualModel>
    ): List<ReasoningLevel?> {
        val reasoning = upstream.capabilities.reasoning
        val rawConfiguredLevels = ReasoningMappingSupport.configuredLevels(reasoning.levels)
        val configuredLevels = rawConfiguredLevels
            .filterNot { level -> level == ReasoningLevel.OFF }
        if (configuredLevels.isNotEmpty()) return configuredLevels
        if (rawConfiguredLevels.isNotEmpty()) return listOf(null)
        if (reasoning.thinkingBudget != null || reasoning.minThinkingBudget != null) {
            return listOf(null)
        }
        if (reasoning.supported == false) return listOf(null)
        if (existingVirtuals.isNotEmpty()) {
            return existingVirtuals.map { virtual -> virtual.defaultReasoningLevel }.distinct()
        }
        if (reasoning.supported == null && reasoning.levels == null) return listOf(null)
        if (reasoning.supported != true && reasoning.levels != null) return listOf(null)
        return ReasoningMappingSupport.defaultLevels(provider.protocol)
    }

    private fun reserveSelectedExistingIds(
        config: AppConfig,
        currentUpstreams: List<UpstreamModel>,
        selectedUpstreamIds: Set<String>,
        occupiedHostIds: MutableSet<String>
    ) {
        currentUpstreams
            .filter { upstream -> upstream.upstreamModelId in selectedUpstreamIds }
            .forEach { upstream ->
                config.virtualModels
                    .filter { virtual -> virtual.upstreamModelId == upstream.id }
                    .mapTo(occupiedHostIds, ModelIdentity::effectiveHostModelId)
            }
    }

    private fun releaseExistingHostIds(
        virtuals: List<VirtualModel>,
        occupiedHostIds: MutableSet<String>
    ) {
        virtuals.forEach { virtual -> occupiedHostIds.remove(ModelIdentity.effectiveHostModelId(virtual)) }
    }
}
