package com.yuzhiqiang.antigravity.domain.model

/**
 * 负责维护暴露给 Antigravity 宿主的稳定模型身份。
 *
 * 宿主看到的模型 ID 不能直接依赖上游模型名称，否则修改 Provider 配置后会
 * 改变宿主选择项并破坏已有请求路由。因此这里统一处理 Host Model ID、目录 ID
 * 以及新建虚拟模型的稳定命名。
 */
object ModelIdentity {
    const val CUSTOM_HOST_MODEL_ID_PREFIX = "MODEL_PLACEHOLDER_M"
    const val CUSTOM_HOST_MODEL_ID_START = 400
    const val CUSTOM_HOST_MODEL_ID_END = 600

   private const val CUSTOM_HOST_MODEL_SLOT_PREFIX = "MODEL_PLACEHOLDER_"
   private const val CUSTOM_MODEL_PREFIX = "custom-"
   private const val CUSTOM_BYOK_MODEL_PREFIX = "custom-byok-"
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

   /** 返回虚拟模型对宿主生效的稳定 Host Model ID。 */
    fun effectiveHostModelId(model: VirtualModel): String {
        val configured = model.hostModelId?.trim()
        return configured.takeUnless { it.isNullOrEmpty() }
            ?: hashedHostModelId(model.id)
    }

    /** 返回没有 VirtualModel 的旧配置所使用的稳定 Host Model ID。 */
    fun effectiveUpstreamHostModelId(model: UpstreamModel): String {
        val configured = model.hostModelId?.trim()
        return configured.takeUnless { it.isNullOrEmpty() }
            ?: hashedHostModelId(model.id.ifBlank { model.upstreamModelId })
    }

    /** 返回宿主目录中使用的自定义虚拟模型 ID。 */
    fun catalogKey(model: VirtualModel): String {
        val prefixedId = if (model.id.startsWith(CUSTOM_MODEL_PREFIX)) {
            model.id
        } else {
            "$CUSTOM_MODEL_PREFIX${model.id}"
        }
        if (!prefixedId.contains('_')) {
            return prefixedId
        }

       val slot = effectiveHostModelId(model)
           .removePrefix(CUSTOM_HOST_MODEL_SLOT_PREFIX)
           .lowercase()
       return "$CUSTOM_BYOK_MODEL_PREFIX$slot"
   }

    /**
     * 对齐 byok configured_model_display_name：
     * - 支持 reasoning 的模型：如果有档位则展示为 "Base (Level)"，无档位则展示为 "Base"（不在名字中混入 Provider，以便 Antigravity 宿主聚类二级子菜单）。
     * - 不支持 reasoning 的模型：展示为 "Base(Provider)"。
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
            val suffixes = listOf(" ($known)", " $known")
            for (suffix in suffixes) {
                if (baseName.endsWith(suffix, ignoreCase = true)) {
                    baseName = baseName.substring(0, baseName.length - suffix.length).trimEnd()
                    break
                }
            }
        }
        if (!supportsReasoning) {
            return "$baseName$providerSuffix"
        }
        val reasoning = reasoningLevel ?: return baseName
        val label = when (reasoning) {
            ReasoningLevel.OFF -> "Off"
            ReasoningLevel.LOW -> "Low"
            ReasoningLevel.MEDIUM -> "Medium"
            ReasoningLevel.HIGH -> "High"
            ReasoningLevel.X_HIGH -> "X-High"
            ReasoningLevel.MAX -> "Max"
            ReasoningLevel.ADAPTIVE -> "Adaptive"
            ReasoningLevel.AUTO -> "Custom"
        }
        return "$baseName ($label)"
    }

    /** 去掉 displayName 末尾的档位后缀（如 ` (High)` / ` (Max)`）。 */
    fun stripDisplayLevelSuffix(displayName: String): String {
        val pattern = Regex("(?i)\\s*(?:\\(|\\s)(?:adaptive|x-high|x_high|medium|auto|custom|default|high|max|low|off)\\)?$")
        return displayName.replace(pattern, "").trim()
    }

   /** 返回虚拟模型可被宿主请求接受的全部别名。 */
    fun acceptedIds(model: VirtualModel): List<String> {
        return listOf(
            normalizeModelId(model.id),
            normalizeModelId(effectiveHostModelId(model)),
            normalizeModelId(catalogKey(model))
        ).distinct()
    }

    /** 去掉宿主目录中用于推理档位聚类的后缀。 */
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

    /** 返回模型族基础 ID；tiered 母条目与具体推理档位共享该族。 */
    fun modelFamilyBase(value: String): String {
        return stripReasoningLevelSuffix(value).removeSuffix("-tiered")
    }

   fun matchesFamilyBase(model: VirtualModel, familyBase: String): Boolean {
       val normalizedBase = normalizeModelId(familyBase)
       return modelFamilyBase(model.id) == normalizedBase ||
               modelFamilyBase(catalogKey(model)) == normalizedBase ||
                modelFamilyBase(catalogFamilyBase(model)) == normalizedBase ||
               modelFamilyBase(effectiveHostModelId(model)) == normalizedBase
   }

    /** 返回虚拟模型的族 base ID（用于母条目 key 构建）。 */
    fun catalogFamilyBase(model: VirtualModel): String {
        val key = catalogKey(model)
        val stripped = modelFamilyBase(key)
        if (stripped != key) {
            return stripped
        }
        val idBase = modelFamilyBase(model.id)
        if (idBase.isNotEmpty() && idBase != model.id) {
            val prefix = if (idBase.startsWith(CUSTOM_MODEL_PREFIX)) "" else CUSTOM_MODEL_PREFIX
            return "$prefix$idBase"
        }
        return key
    }

   /** 为模型保留显式或确定性 Host Model ID，发生冲突时再分配空闲槽位。 */
    fun resolveHostModelId(
        seed: String,
        configuredId: String?,
        occupiedIds: MutableSet<String>
    ): Result<String> {
        val candidate = configuredId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: hashedHostModelId(seed)
        if (occupiedIds.add(candidate)) {
            return Result.success(candidate)
        }
        return allocateHostModelId(occupiedIds)
    }

    /** 从已占用的 Host Model ID 中分配下一个稳定槽位。 */
    fun allocateHostModelId(occupiedIds: MutableSet<String>): Result<String> {
        for (slot in CUSTOM_HOST_MODEL_ID_START until CUSTOM_HOST_MODEL_ID_END) {
            val candidate = "$CUSTOM_HOST_MODEL_ID_PREFIX$slot"
            if (occupiedIds.add(candidate)) {
                return Result.success(candidate)
            }
        }
        return Result.failure(IllegalStateException("自定义模型 Host Model ID 槽位已耗尽"))
    }

    /** 根据上游模型名称生成不会覆盖其他模型的虚拟模型 ID。 */
    fun createVirtualModelId(
        model: UpstreamModel,
        occupiedIds: MutableSet<String>,
        reasoningLevel: ReasoningLevel? = null
    ): String {
        val source = model.displayName?.trim().orEmpty()
            .ifBlank { model.upstreamModelId.trim() }
        val slug = source
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "model" }
        val reasoningSuffix = reasoningLevel?.name
            ?.lowercase()
            ?.replace('_', '-')
            ?.let { "-$it" }
            .orEmpty()
        val baseId = "$CUSTOM_MODEL_PREFIX$slug$reasoningSuffix"
        var candidate = baseId
        var suffix = 1
        while (!occupiedIds.add(candidate)) {
            candidate = "$baseId-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun hashedHostModelId(seed: String): String {
        var hash = 0x811c9dc5u
        seed.encodeToByteArray().forEach { byteValue ->
            hash = (hash xor (byteValue.toInt() and 0xff).toUInt()) * 0x01000193u
        }
        val slotCount = CUSTOM_HOST_MODEL_ID_END - CUSTOM_HOST_MODEL_ID_START
        val slot = CUSTOM_HOST_MODEL_ID_START + (hash % slotCount.toUInt()).toInt()
        return "$CUSTOM_HOST_MODEL_ID_PREFIX$slot"
    }

    private fun normalizeModelId(value: String): String {
        return value.trim().removePrefix(MODEL_NAMESPACE_PREFIX)
    }
}
