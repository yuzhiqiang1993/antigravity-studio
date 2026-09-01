package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.domain.model.AppConfig
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicyAssignment
import kotlinx.serialization.json.*

object CatalogInjector {

    fun injectCustomModels(
        root: JsonObject,
        config: AppConfig,
        includeTiered: Boolean = true
    ): JsonObject {
        val checkpointWorkers = CatalogCompressionApplier.checkpointWorkerIds(root)
        val defaultCheckpointWorker = checkpointWorkers
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key
        val entries = customCatalogEntries(
            config,
            includeTiered,
            checkpointWorkers,
            defaultCheckpointWorker
        )
        if (entries.isEmpty()) return root
        val response = root["response"] as? JsonObject
        return if (response?.get("models") != null) {
            val updatedResponse = appendCatalogEntries(response, entries)
            val withRoles = injectRoleReferences(updatedResponse, entries)
            JsonObject(root + ("response" to injectTieredReferences(withRoles, entries)))
        } else {
            injectTieredReferences(injectRoleReferences(appendCatalogEntries(root, entries), entries), entries)
        }
    }

    fun removeDisabledOfficialModels(
        root: JsonObject,
        disabledModelIds: List<String>
    ): JsonObject {
        return CatalogModelFilter.removeDisabledOfficialModels(root, disabledModelIds)
    }

    fun applyOfficialCompressionPolicies(
        root: JsonObject,
        assignments: List<ModelCompressionPolicyAssignment>
    ): JsonObject {
        return CatalogCompressionApplier.applyOfficialCompressionPolicies(root, assignments)
    }

    fun customCatalogEntries(
        config: AppConfig,
        includeTiered: Boolean = true,
        checkpointWorkers: Collection<String> = emptySet(),
        defaultCheckpointWorker: String? = null
    ): List<JsonObject> {
        return CustomCatalogBuilder.customCatalogEntries(
            config,
            includeTiered,
            checkpointWorkers,
            defaultCheckpointWorker
        )
    }

    private fun injectTieredReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        if (container["models"] !is JsonObject) return container
        val tieredIds = entries.mapNotNull { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.endsWith("-tiered") }
        }.distinct()
        if (tieredIds.isEmpty()) return container
        val current = container["tieredModelIds"] as? JsonObject ?: JsonObject(emptyMap())
        val existing = (current["custom"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        tieredIds.forEach { id ->
            if (existing.none { it.jsonPrimitive.contentOrNull == id }) existing += JsonPrimitive(id)
        }
        return JsonObject(container + ("tieredModelIds" to JsonObject(current + ("custom" to JsonArray(existing)))))
    }

    private fun injectRoleReferences(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        val result = container.toMutableMap()
        val roleEntries = entries.filterNot { entry ->
            entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
        }
        val agentIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
        val imageIds = roleEntries.filter { entry ->
            (entry["roles"] as? JsonArray)?.any {
                it.jsonPrimitive.contentOrNull.equals("image_generation", ignoreCase = true)
            } == true
        }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }

        val existingSorts = result["agentModelSorts"] as? JsonArray
        if (existingSorts != null && agentIds.isNotEmpty()) {
            val sorts = existingSorts.toMutableList()
            val nonAgentIds = roleEntries.filterNot { entry ->
                (entry["roles"] as? JsonArray)?.any {
                    it.jsonPrimitive.contentOrNull.equals("agent", ignoreCase = true)
                } == true
            }.mapNotNull { it["catalogKey"]?.jsonPrimitive?.contentOrNull ?: it["id"]?.jsonPrimitive?.contentOrNull }
                .toSet()
            sorts.indices.forEach { sortIndex ->
                val sortObject = sorts[sortIndex] as? JsonObject ?: return@forEach
                val groups = sortObject["groups"] as? JsonArray ?: return@forEach
                sorts[sortIndex] = JsonObject(sortObject + ("groups" to JsonArray(groups.map { group ->
                    val groupObject = group as? JsonObject ?: return@map group
                    val currentIds = (groupObject["modelIds"] as? JsonArray)?.toMutableList() ?: mutableListOf()
                    currentIds.removeAll { it.jsonPrimitive.contentOrNull in nonAgentIds }
                    agentIds.forEach { id ->
                        if (currentIds.none { it.jsonPrimitive.contentOrNull == id }) currentIds += JsonPrimitive(id)
                    }
                    JsonObject(groupObject + ("modelIds" to JsonArray(currentIds)))
                })))
            }
            val byokIndex = sorts.indexOfFirst {
                (it as? JsonObject)?.get("displayName")?.jsonPrimitive?.contentOrNull == "BYOK"
            }
            val targetIndex = if (byokIndex >= 0) byokIndex else {
                sorts += buildJsonObject { put("displayName", "BYOK"); put("groups", JsonArray(emptyList())) }
                sorts.lastIndex
            }
            val target = sorts[targetIndex] as? JsonObject ?: JsonObject(emptyMap())
            val groups = mutableListOf<JsonElement>().apply {
                addAll(
                    (target["groups"] as? JsonArray)?.toList()
                        ?: listOf(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
                )
                if (isEmpty()) add(buildJsonObject { put("modelIds", JsonArray(emptyList())) })
            }
            val firstGroup = groups.firstOrNull() as? JsonObject ?: JsonObject(emptyMap())
            val modelIds = mutableListOf<JsonElement>().apply {
                addAll((firstGroup["modelIds"] as? JsonArray)?.toList().orEmpty())
            }
            agentIds.forEach { id ->
                if (modelIds.none { it.jsonPrimitive.contentOrNull == id }) modelIds += JsonPrimitive(id)
            }
            groups[0] = JsonObject(firstGroup + ("modelIds" to JsonArray(modelIds)))
            sorts[targetIndex] = JsonObject(target + ("groups" to JsonArray(groups)))
            result["agentModelSorts"] = JsonArray(sorts)
        }

        val existingImageIds = result["imageGenerationModelIds"] as? JsonArray
        if (imageIds.isNotEmpty()) {
            val merged = existingImageIds?.toMutableList() ?: mutableListOf()
            imageIds.forEach { id ->
                if (merged.none { it.jsonPrimitive.contentOrNull == id }) merged += JsonPrimitive(id)
            }
            result["imageGenerationModelIds"] = JsonArray(merged)
        }
        return JsonObject(result)
    }

    private fun appendCatalogEntries(
        container: JsonObject,
        entries: List<JsonObject>
    ): JsonObject {
        return when (val models = container["models"]) {
            is JsonArray -> {
                val arrayEntries = entries.filterNot { entry ->
                    entry["catalogKey"]?.jsonPrimitive?.contentOrNull?.endsWith("-tiered") == true
                }
                val existing = models.mapNotNull { entry ->
                    CatalogModelFilter.catalogModelId(entry)
                }.toSet()
                val additions = arrayEntries.filterNot { entry ->
                    val catalogModelId = CatalogModelFilter.catalogModelId(entry)
                    catalogModelId != null && catalogModelId in existing
                }
                JsonObject(container + ("models" to JsonArray(models + additions)))
            }

            is JsonObject -> {
                val updated = models.toMutableMap()
                entries.forEach { entry ->
                    val key = entry["catalogKey"]?.jsonPrimitive?.contentOrNull
                        ?: entry["id"]?.jsonPrimitive?.contentOrNull
                        ?: CatalogModelFilter.catalogModelId(entry)
                        ?: return@forEach
                    updated[key] = JsonObject(entry - setOf("id", "name"))
                }
                JsonObject(container + ("models" to JsonObject(updated)))
            }

            else -> JsonObject(container + ("models" to JsonArray(entries)))
        }
    }
}
