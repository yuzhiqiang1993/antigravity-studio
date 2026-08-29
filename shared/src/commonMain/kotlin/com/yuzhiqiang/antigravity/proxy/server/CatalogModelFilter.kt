package com.yuzhiqiang.antigravity.proxy.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object CatalogModelFilter {

    fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        val disabled = disabledModelIds.map(::normalizeCatalogModelId).toMutableSet()
        officialModelAliases(root).forEach { (deprecated, replacement) ->
            if (normalizeCatalogModelId(deprecated) in disabled) disabled += normalizeCatalogModelId(replacement)
            if (normalizeCatalogModelId(replacement) in disabled) disabled += normalizeCatalogModelId(deprecated)
        }
        val filteredRoot = filterCatalogContainer(root, disabled)
        val response = filteredRoot["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(filteredRoot + ("response" to filterCatalogContainer(response, disabled)))
        } else {
            filteredRoot
        }
    }

    fun officialModelAliases(root: JsonObject): Map<String, String> {
        val aliases = linkedMapOf<String, String>()
        fun collect(container: JsonObject) {
            val deprecated = container["deprecatedModelIds"] as? JsonObject ?: return
            deprecated.forEach { (oldId, value) ->
                val newId = (value as? JsonObject)?.get("newModelId")?.jsonPrimitive?.contentOrNull
                if (!newId.isNullOrBlank()) aliases[oldId] = newId
            }
        }
        collect(root)
        (root["response"] as? JsonObject)?.let(::collect)
        return aliases
    }

    fun filterCatalogContainer(
        container: JsonObject,
        disabled: Set<String>
    ): JsonObject {
        val filteredContainer = when (val models = container["models"]) {
            is JsonArray -> JsonObject(
                container + ("models" to JsonArray(models.filterNot { isDisabledCatalogModel(it, null, disabled) }))
            )

            is JsonObject -> {
                val filtered = models.filterNot { (key, value) ->
                    isDisabledCatalogModel(value, key, disabled)
                }
                JsonObject(container + ("models" to JsonObject(filtered)))
            }

            else -> container
        }

        fun filterIdArray(value: JsonElement?): JsonElement? {
            val array = value as? JsonArray ?: return value
            return JsonArray(array.filterNot { item ->
                normalizeCatalogModelId(item.jsonPrimitive.contentOrNull.orEmpty()) in disabled
            })
        }

        val updated = filteredContainer.toMutableMap()
        if (filteredContainer["agentModelSorts"] != null) {
            updated["agentModelSorts"] = filterSortGroups(filteredContainer["agentModelSorts"], disabled)
        }
        if (filteredContainer["imageGenerationModelIds"] != null) {
            updated["imageGenerationModelIds"] = filterIdArray(filteredContainer["imageGenerationModelIds"])
                ?: JsonArray(emptyList())
        }
        return JsonObject(updated)
    }

    fun filterSortGroups(value: JsonElement?, disabled: Set<String>): JsonElement {
        val sorts = value as? JsonArray ?: return value ?: JsonArray(emptyList())
        return JsonArray(sorts.map { sort ->
            val sortObject = sort as? JsonObject ?: return@map sort
            val groups = sortObject["groups"] as? JsonArray ?: return@map sort
            JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                val groupObject = group as? JsonObject ?: return@map group
                val ids = groupObject["modelIds"] as? JsonArray ?: return@map group
                JsonObject(groupObject + ("modelIds" to JsonArray(ids.filterNot { id ->
                    normalizeCatalogModelId(id.jsonPrimitive.contentOrNull.orEmpty()) in disabled
                })))
            })))
        })
    }

    fun isDisabledCatalogModel(
        element: JsonElement,
        key: String?,
        disabled: Set<String>
    ): Boolean {
        return catalogModelIds(element, key).any { normalizeCatalogModelId(it) in disabled }
    }

    fun catalogModelIds(element: JsonElement, key: String? = null): List<String> {
        val value = element as? JsonObject
        return listOfNotNull(
            key,
            value?.get("name")?.toString()?.trim('"'),
            value?.get("model")?.toString()?.trim('"'),
            value?.get("id")?.toString()?.trim('"'),
            value?.get("catalogKey")?.toString()?.trim('"')
        )
    }

    fun normalizeCatalogModelId(value: String): String {
        return value.trim().removePrefix("models/")
    }
}
