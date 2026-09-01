package com.yuzhiqiang.antigravity.domain.model

/**
 * 维护暴露给 Antigravity 宿主的目录与运行时身份。
 *
 * 这里不解析真实模型身份：catalog/runtime ID 只服务宿主路由，canonical 解析由
 * [ModelIdentityRegistry] 负责，显示名称也绝不参与稳定 ID 生成。
 */
object ModelIdentity {
    const val CUSTOM_RUNTIME_MODEL_ID_PREFIX = "MODEL_PLACEHOLDER_M"
    const val CUSTOM_RUNTIME_MODEL_ID_START = 400
    const val CUSTOM_RUNTIME_MODEL_ID_END = 600

    private const val MODEL_NAMESPACE_PREFIX = "models/"

    val REASONING_LEVEL_PRIORITY = listOf(
        ReasoningLevel.HIGH,
        ReasoningLevel.MEDIUM,
        ReasoningLevel.LOW,
        ReasoningLevel.X_HIGH,
        ReasoningLevel.MAX,
        ReasoningLevel.ADAPTIVE,
        ReasoningLevel.AUTO,
        ReasoningLevel.OFF
    )

    fun effectiveRuntimeModelId(variant: ModelRouteVariant): String = variant.runtimeModelId.trim()

    fun catalogKey(variant: ModelRouteVariant): String = normalizeModelId(variant.catalogModelId)

    /**
     * 对齐宿主目录的显示规则。此函数只生成展示文本，返回值不能用于 identity、
     * 聚合、路由或价格匹配。
     */
    fun configuredModelDisplayName(
        modelName: String,
        reasoningLevel: ReasoningLevel?,
        providerName: String,
        supportsReasoning: Boolean
    ): String {
        val providerSuffix = "($providerName)"
        var baseName = modelName.trim()
        if (baseName.endsWith(providerSuffix, ignoreCase = true)) {
            baseName = baseName.substring(0, baseName.length - providerSuffix.length).trimEnd()
        }
        val knownReasoning = listOf(
            "default", "off", "low", "medium", "high", "xhigh", "x-high", "max", "adaptive", "auto", "custom"
        )
        for (known in knownReasoning) {
            for (suffix in listOf(" ($known)", " $known")) {
                if (baseName.endsWith(suffix, ignoreCase = true)) {
                    baseName = baseName.substring(0, baseName.length - suffix.length).trimEnd()
                    break
                }
            }
        }
        if (!supportsReasoning) return "$baseName$providerSuffix"
        val level = reasoningLevel ?: return baseName
        return "$baseName (${reasoningLabel(level)})"
    }

    fun stripDisplayLevelSuffix(displayName: String): String {
        val pattern =
            Regex("(?i)\\s*(?:\\(|\\s)(?:adaptive|x-high|x_high|medium|auto|custom|default|high|max|low|off)\\)?$")
        return displayName.replace(pattern, "").trim()
    }

    fun acceptedIds(variant: ModelRouteVariant): List<String> = listOf(
        normalizeModelId(variant.variantId),
        normalizeModelId(effectiveRuntimeModelId(variant)),
        catalogKey(variant)
    ).distinct()

    fun stripReasoningLevelSuffix(value: String): String {
        val normalized = normalizeModelId(value)
        val suffixes = listOf(
            "-adaptive", "-x-high", "-x_high", "-medium", "-auto",
            "-high", "-max", "-low", "-off"
        )
        return suffixes.firstNotNullOfOrNull { suffix ->
            normalized.removeSuffix(suffix).takeIf { it != normalized }
        } ?: normalized
    }

    fun modelFamilyBase(value: String): String = stripReasoningLevelSuffix(value).removeSuffix("-tiered")

    fun matchesFamilyBase(variant: ModelRouteVariant, familyBase: String): Boolean {
        val normalizedBase = normalizeModelId(familyBase)
        return modelFamilyBase(variant.variantId) == normalizedBase ||
                modelFamilyBase(catalogKey(variant)) == normalizedBase ||
                modelFamilyBase(catalogFamilyBase(variant)) == normalizedBase ||
                modelFamilyBase(effectiveRuntimeModelId(variant)) == normalizedBase
    }

    fun catalogFamilyBase(variant: ModelRouteVariant): String = modelFamilyBase(catalogKey(variant))

    fun resolveRuntimeModelId(
        seed: String,
        configuredId: String?,
        occupiedIds: MutableSet<String>
    ): Result<String> {
        val candidate = configuredId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: hashedRuntimeModelId(seed)
        if (occupiedIds.add(candidate)) return Result.success(candidate)
        return allocateRuntimeModelId(occupiedIds)
    }

    fun allocateRuntimeModelId(occupiedIds: MutableSet<String>): Result<String> {
        for (slot in CUSTOM_RUNTIME_MODEL_ID_START until CUSTOM_RUNTIME_MODEL_ID_END) {
            val candidate = "$CUSTOM_RUNTIME_MODEL_ID_PREFIX$slot"
            if (occupiedIds.add(candidate)) return Result.success(candidate)
        }
        return Result.failure(IllegalStateException("自定义模型 Runtime Model ID 槽位已耗尽"))
    }

    fun createModelRouteVariantId(
        binding: ProviderModelBinding,
        occupiedIds: MutableSet<String>,
        reasoningLevel: ReasoningLevel? = null
    ): String {
        val levelSuffix = reasoningLevel?.let { "-${reasoningSlug(it)}" }.orEmpty()
        return reserveUnique("variant-${stableHash(binding.bindingId)}$levelSuffix", occupiedIds)
    }

    fun createCatalogModelId(
        binding: ProviderModelBinding,
        occupiedIds: MutableSet<String>,
        reasoningLevel: ReasoningLevel? = null
    ): String {
        val readable = binding.providerModelId
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(32)
            .ifBlank { "model" }
        val levelSuffix = reasoningLevel?.let { "-${reasoningSlug(it)}" }.orEmpty()
        val seed = "${binding.providerConfigId}\u0000${binding.providerModelId}"
        return reserveUnique("byok-$readable-${stableHash(seed)}$levelSuffix", occupiedIds)
    }

    fun normalizeModelId(value: String): String = value.trim().removePrefix(MODEL_NAMESPACE_PREFIX)

    private fun reserveUnique(baseId: String, occupiedIds: MutableSet<String>): String {
        var candidate = baseId
        var suffix = 1
        while (!occupiedIds.add(candidate)) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun hashedRuntimeModelId(seed: String): String {
        val slotCount = CUSTOM_RUNTIME_MODEL_ID_END - CUSTOM_RUNTIME_MODEL_ID_START
        val slot = CUSTOM_RUNTIME_MODEL_ID_START + (fnv1a(seed) % slotCount.toUInt()).toInt()
        return "$CUSTOM_RUNTIME_MODEL_ID_PREFIX$slot"
    }

    private fun stableHash(value: String): String = fnv1a(value).toString(16).padStart(8, '0')

    private fun fnv1a(value: String): UInt {
        var hash = 0x811c9dc5u
        value.encodeToByteArray().forEach { byteValue ->
            hash = (hash xor (byteValue.toInt() and 0xff).toUInt()) * 0x01000193u
        }
        return hash
    }

    private fun reasoningSlug(level: ReasoningLevel): String = level.name.lowercase().replace('_', '-')

    private fun reasoningLabel(level: ReasoningLevel): String = when (level) {
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
