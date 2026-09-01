package com.yuzhiqiang.antigravity.proxy.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object CatalogModelFilter {

    private val roleModelIdFields = listOf(
        "commandModelIds",
        "tabModelIds",
        "imageGenerationModelIds",
        "mqueryModelIds",
        "webSearchModelIds",
        "commitMessageModelIds",
        "audioTranscriptionModelIds"
    )

    fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        val disabled = disabledModelIds.map(::normalizeCatalogModelId).toSet()
        if (disabled.isEmpty()) return root
        val filteredRoot = filterCatalogContainer(root, disabled)
        val response = filteredRoot["response"] as? JsonObject
        return if (response?.get("models") != null) {
            JsonObject(filteredRoot + ("response" to filterCatalogContainer(response, disabled)))
        } else {
            filteredRoot
        }
    }

    fun filterCatalogContainer(
        container: JsonObject,
        disabled: Set<String>
    ): JsonObject {
        val filteredContainer = when (val models = container["models"]) {
            is JsonArray -> JsonObject(
                container + ("models" to JsonArray(models.filterNot { element ->
                    isDisabledCatalogModel(element, null, disabled)
                }))
            )

            is JsonObject -> JsonObject(
                container + ("models" to JsonObject(models.filterNot { (key, value) ->
                    isDisabledCatalogModel(value, key, disabled)
                }))
            )

            else -> container
        }

        val updated = filteredContainer.toMutableMap()
        updated["agentModelSorts"]?.let { value ->
            updated["agentModelSorts"] = filterSortGroups(value, disabled)
        }
        roleModelIdFields.forEach { field ->
            updated[field]?.let { value -> updated[field] = filterIdArray(value, disabled) }
        }
        updated["clientModelRoles"]?.let { value ->
            updated["clientModelRoles"] = filterModelIdReferences(value, disabled)
        }
        updated["tieredModelIds"]?.let { value ->
            updated["tieredModelIds"] = filterModelIdReferences(value, disabled)
        }
        (updated["defaultAgentModelId"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { normalizeCatalogModelId(it) in disabled }
            ?.let { updated.remove("defaultAgentModelId") }
        (updated["deprecatedModelIds"] as? JsonObject)?.let { deprecated ->
            updated["deprecatedModelIds"] = JsonObject(deprecated.filterNot { (oldId, replacement) ->
                val replacementId = replacementCatalogModelId(replacement)
                normalizeCatalogModelId(oldId) in disabled ||
                        (replacementId != null && normalizeCatalogModelId(replacementId) in disabled)
            })
        }
        return JsonObject(updated)
    }

    fun filterSortGroups(value: JsonElement, disabled: Set<String>): JsonElement {
        val sorts = value as? JsonArray ?: return value
        return JsonArray(sorts.map { sort ->
            val sortObject = sort as? JsonObject ?: return@map sort
            val groups = sortObject["groups"] as? JsonArray ?: return@map sort
            JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                val groupObject = group as? JsonObject ?: return@map group
                val ids = groupObject["modelIds"] ?: return@map group
                JsonObject(groupObject + ("modelIds" to filterIdArray(ids, disabled)))
            })))
        })
    }

    fun isDisabledCatalogModel(
        element: JsonElement,
        key: String?,
        disabled: Set<String>
    ): Boolean {
        val catalogModelId = catalogModelId(element, key) ?: return false
        return normalizeCatalogModelId(catalogModelId) in disabled
    }

    /** Object catalog 使用 map key；array catalog 只接受显式 catalogKey/id。 */
    fun catalogModelId(element: JsonElement, key: String? = null): String? {
        key?.takeIf(String::isNotBlank)?.let { return it }
        val value = element as? JsonObject ?: return null
        return value["catalogKey"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: value["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }

    fun normalizeCatalogModelId(value: String): String =
        value.trim().removePrefix("models/")

    private fun filterIdArray(value: JsonElement, disabled: Set<String>): JsonElement {
        val array = value as? JsonArray ?: return value
        return JsonArray(array.filterNot { item ->
            val id = (item as? JsonPrimitive)?.contentOrNull ?: return@filterNot false
            normalizeCatalogModelId(id) in disabled
        })
    }

    private fun filterModelIdReferences(value: JsonElement, disabled: Set<String>): JsonElement {
        return when (value) {
            is JsonArray -> JsonArray(value.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.takeUnless {
                        normalizeCatalogModelId(it.contentOrNull.orEmpty()) in disabled
                    }

                    is JsonObject -> filterReferenceObject(item, disabled)
                    else -> item
                }
            })

            is JsonObject -> JsonObject(value.mapNotNull { (key, nested) ->
                val disabledPrimitive = (nested as? JsonPrimitive)
                    ?.contentOrNull
                    ?.let(::normalizeCatalogModelId)
                    ?.let(disabled::contains)
                    ?: false
                if (disabledPrimitive) null else key to filterModelIdReferences(nested, disabled)
            }.toMap())

            else -> value
        }
    }

    private fun filterReferenceObject(value: JsonObject, disabled: Set<String>): JsonObject {
        val updated = value.toMutableMap()
        listOf("modelIds", "models", "ids").forEach { field ->
            updated[field]?.let { nested ->
                updated[field] = filterModelIdReferences(nested, disabled)
            }
        }
        return JsonObject(updated)
    }

    private fun replacementCatalogModelId(value: JsonElement): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value["newModelId"]?.jsonPrimitive?.contentOrNull
            ?: value["replacementModelId"]?.jsonPrimitive?.contentOrNull
            ?: value["replacement"]?.jsonPrimitive?.contentOrNull

        else -> null
    }
}
