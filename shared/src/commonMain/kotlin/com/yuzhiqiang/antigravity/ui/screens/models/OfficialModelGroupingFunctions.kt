package com.yuzhiqiang.antigravity.ui.screens.models

import com.yuzhiqiang.antigravity.domain.model.OfficialCatalogModel

fun filterMainAgentModels(models: List<OfficialCatalogModel>): List<OfficialCatalogModel> {
    val hasAgentMetadata = models.any { it.roles.isNotEmpty() }
    val filtered = if (hasAgentMetadata) {
        models.filter { it.roles.contains("agent") && !it.isDeprecated && it.isRecommended }
    } else {
        models.filterNot { it.isDeprecated }
    }

    return filtered.sortedWith(
        compareBy<OfficialCatalogModel> { it.agentSortOrder ?: Long.MAX_VALUE }
            .thenBy { it.id }
    )
}

fun groupOfficialModels(models: List<OfficialCatalogModel>): List<GroupedOfficialModel> {
    val mainModels = filterMainAgentModels(models)
    if (mainModels.isEmpty()) return emptyList()

    val regex = Regex("""^(.*?)(?:\s*\((.*?)\))?$""")
    val groupMap = linkedMapOf<String, MutableList<OfficialModelVariant>>()
    val baseItemMap = mutableMapOf<String, OfficialCatalogModel>()

    for (model in mainModels) {
        val rawName = model.displayName.ifBlank { model.id }
        val match = regex.find(rawName)
        val baseName = match?.groupValues?.getOrNull(1)?.trim() ?: rawName
        val variantLabel = match?.groupValues?.getOrNull(2)?.trim()?.ifBlank { null }
            ?: if (model.supportsReasoning) "Thinking" else "Default"
        baseItemMap.putIfAbsent(baseName, model)
        groupMap.getOrPut(baseName) { mutableListOf() }
            .add(OfficialModelVariant(variantLabel, model))
    }

    val levelOrder = mapOf(
        "High" to 1,
        "Medium" to 2,
        "Low" to 3,
        "Thinking" to 4,
        "Max" to 5,
        "Default" to 6
    )
    return groupMap.map { (baseName, variants) ->
        val sortedVariants = variants.sortedBy { levelOrder[it.label] ?: 99 }
        GroupedOfficialModel(
            baseName = baseName,
            baseItem = baseItemMap[baseName] ?: sortedVariants.first().model,
            variants = sortedVariants
        )
    }
}
