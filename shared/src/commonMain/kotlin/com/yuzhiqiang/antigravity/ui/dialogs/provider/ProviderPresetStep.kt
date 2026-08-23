package com.yuzhiqiang.antigravity.ui.dialogs.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.data.presets.PresetCategory
import com.yuzhiqiang.antigravity.data.presets.PresetProviderTemplate
import com.yuzhiqiang.antigravity.data.presets.ProviderPresets
import com.yuzhiqiang.antigravity.ui.components.StudioSearchField
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun ProviderPresetStep(
    selectedPresetId: String?,
    onSelectPreset: (PresetProviderTemplate) -> Unit,
    onSelectCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var presetCategory by remember { mutableStateOf(PresetCategory.ALL) }
    var presetSearchQuery by remember { mutableStateOf("") }
    val statusColors = AppStatusColors

    val filteredPresets = remember(presetCategory, presetSearchQuery) {
        val list = if (presetCategory == PresetCategory.ALL) {
            ProviderPresets.allPresets
        } else {
            ProviderPresets.allPresets.filter { it.category == presetCategory }
        }
        if (presetSearchQuery.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(presetSearchQuery, ignoreCase = true) ||
                        it.defaultBaseUrl.contains(presetSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    PresetCategory.ALL to "全部",
                    PresetCategory.AGGREGATOR to "聚合网关",
                    PresetCategory.RECOMMENDED to "常用推荐",
                    PresetCategory.OFFICIAL to "官方厂商",
                    PresetCategory.LOCAL_CUSTOM to "本地/自定义"
                ).forEach { (cat, label) ->
                    val selected = presetCategory == cat
                    val background = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(background)
                            .clickable { presetCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor
                        )
                    }
                }
            }

            StudioSearchField(
                value = presetSearchQuery,
                onValueChange = { presetSearchQuery = it },
                placeholder = "搜索服务商名称...",
                modifier = Modifier.width(220.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = AppTokens.Size.presetGridMinWidth),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItems(filteredPresets, key = { it.id }) { preset ->
                val isSelected = selectedPresetId == preset.id
                val isCustom = preset.category == PresetCategory.LOCAL_CUSTOM
                val tagText = when (preset.category) {
                    PresetCategory.AGGREGATOR -> "聚合"
                    PresetCategory.RECOMMENDED -> "推荐"
                    PresetCategory.OFFICIAL -> "官方"
                    PresetCategory.LOCAL_CUSTOM -> "本地"
                    PresetCategory.ALL -> "自定义"
                }
                val tagBackground = when (preset.category) {
                    PresetCategory.OFFICIAL -> MaterialTheme.colorScheme.primaryContainer
                    PresetCategory.LOCAL_CUSTOM -> statusColors.successContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val tagColor = when (preset.category) {
                    PresetCategory.OFFICIAL -> MaterialTheme.colorScheme.primary
                    PresetCategory.LOCAL_CUSTOM -> statusColors.success
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val cardBackground = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                }

                val cardModifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius.small))
                    .then(
                        if (!isSelected) {
                            Modifier.dashedBorder(
                                width = 1.2.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                cornerRadius = AppTokens.Radius.small,
                                dashLength = 4.dp,
                                gapLength = 3.5.dp
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable {
                        if (preset.id == "custom_openai") {
                            onSelectCustom()
                        } else {
                            onSelectPreset(preset)
                        }
                    }

                Surface(
                    modifier = cardModifier,
                    shape = RoundedCornerShape(AppTokens.Radius.small),
                    color = cardBackground,
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(
                            width = 1.8.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isCustom) statusColors.successContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.name.trim().firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCustom) statusColors.success else MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tagBackground)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = tagText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = tagColor
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

