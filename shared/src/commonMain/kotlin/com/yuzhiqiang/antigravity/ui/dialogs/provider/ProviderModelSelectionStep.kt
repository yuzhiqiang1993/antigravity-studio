package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.domain.model.ModelModality
import com.yuzhiqiang.antigravity.domain.model.Provider
import com.yuzhiqiang.antigravity.network.ConnectionTester
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ProviderModelSelectionStep(
    fetchedModelConfigs: List<CatalogModelConfig>,
    selectedModelIds: Set<String>,
    onSelectedModelIdsChange: (Set<String>) -> Unit,
    isSingleModelMode: Boolean,
    onConfigureReasoning: (String) -> Unit,
    onUpdateModelConfig: (CatalogModelConfig) -> Unit,
    currentProvider: () -> Provider,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var modelSearchQuery by remember { mutableStateOf("") }
    var modelFilterTab by remember { mutableStateOf(ModelSelectionFilter.ALL) }

    val filteredFetchedModels = remember(fetchedModelConfigs, modelSearchQuery, modelFilterTab, selectedModelIds) {
        fetchedModelConfigs.filter { model ->
            val matchSearch = modelSearchQuery.isBlank() ||
                    model.name.contains(modelSearchQuery, ignoreCase = true) ||
                    model.id.contains(modelSearchQuery, ignoreCase = true)

            val isSelected = selectedModelIds.contains(model.id)
            val matchFilter = when (modelFilterTab) {
                ModelSelectionFilter.ALL -> true
                ModelSelectionFilter.SELECTED -> isSelected
                ModelSelectionFilter.UNSELECTED -> !isSelected
            }

            matchSearch && matchFilter
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        if (!isSingleModelMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioSearchField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        placeholder = "搜索模型名称或 ID...",
                        modifier = Modifier.width(220.dp)
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTokens.Radius.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val totalCount = fetchedModelConfigs.size
                        val selCount = selectedModelIds.size
                        val unselCount = totalCount - selCount

                        listOf(
                            ModelSelectionFilter.ALL to "全部 ($totalCount)",
                            ModelSelectionFilter.SELECTED to "已选 ($selCount)",
                            ModelSelectionFilter.UNSELECTED to "未选 ($unselCount)"
                        ).forEach { (filter, label) ->
                            val isTabActive = modelFilterTab == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                    .background(
                                        if (isTabActive) MaterialTheme.colorScheme.surface
                                        else Color.Transparent
                                    )
                                    .clickable { modelFilterTab = filter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isTabActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allVisibleIds = filteredFetchedModels.map { it.id }.toSet()
                    val isAllSelected =
                        allVisibleIds.isNotEmpty() && allVisibleIds.all { it in selectedModelIds }

                    OutlinedButton(
                        onClick = {
                            if (isAllSelected) {
                                onSelectedModelIdsChange(selectedModelIds - allVisibleIds)
                            } else {
                                onSelectedModelIdsChange(selectedModelIds + allVisibleIds)
                            }
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.small),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            if (isAllSelected) "取消全选" else "全选 (${filteredFetchedModels.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
                        )
                    }
                }
            }
        }

        if (filteredFetchedModels.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (modelSearchQuery.isNotBlank()) "未搜索到匹配「$modelSearchQuery」的模型" else "当前筛选下暂无模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredFetchedModels, key = { it.id }) { modelConfig ->
                    val isChecked = selectedModelIds.contains(modelConfig.id)
                    CatalogModelRowCard(
                        config = modelConfig,
                        isChecked = isChecked,
                        isSingleMode = isSingleModelMode,
                        onToggleCheck = {
                            onSelectedModelIdsChange(
                                if (isChecked) selectedModelIds - modelConfig.id else selectedModelIds + modelConfig.id
                            )
                        },
                        onConfigureReasoning = {
                            onConfigureReasoning(modelConfig.id)
                        },
                        onTokenLimitChanged = onUpdateModelConfig,
                        onToolsChanged = onUpdateModelConfig,
                        onVisionChanged = { updatedConfig ->
                            val modalities = updatedConfig.inputModalities
                                .toMutableSet()
                                .apply {
                                    add(ModelModality.TEXT)
                                    if (updatedConfig.isVision) add(ModelModality.IMAGE)
                                    else remove(ModelModality.IMAGE)
                                }
                            val mimeTypes = updatedConfig.inputMimeTypes
                                .toMutableSet()
                                .apply {
                                    if (updatedConfig.isVision && none { it.startsWith("image/", ignoreCase = true) }) {
                                        addAll(listOf("image/png", "image/jpeg", "image/webp"))
                                    }
                                    if (!updatedConfig.isVision) {
                                        removeAll { it.startsWith("image/", ignoreCase = true) }
                                    }
                                }
                            onUpdateModelConfig(
                                updatedConfig.copy(
                                    inputModalities = modalities,
                                    inputMimeTypes = mimeTypes.toList().sorted()
                                )
                            )
                        },
                        onTestModel = {
                            coroutineScope.launch {
                                onUpdateModelConfig(modelConfig.copy(isTesting = true, testStatusText = null))
                                val tempProvider = currentProvider()
                                val result = ConnectionTester.testProvider(
                                    tempProvider,
                                    modelConfig.id,
                                    imageOnly = modelConfig.isImageGeneration
                                )
                                onUpdateModelConfig(
                                    modelConfig.copy(
                                        isTesting = false,
                                        isTestSuccess = result.success,
                                        testStatusText = if (result.success) "${result.latencyMs}ms" else "失败"
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

