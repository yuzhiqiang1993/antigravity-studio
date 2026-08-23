package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalModelCard(
    state: UniversalModelCardUiState,
    modifier: Modifier = Modifier
) {
    val cardAlpha = if (state.isEnabled) 1f else 0.55f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (state.isEnabled) 1.dp else 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.subtitle != null) {
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.supportsVision) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.Image,
                            contentDescription = "多模态能力",
                            onClick = state.onOpenVisionDetail
                        )
                    }
                    if (state.supportsTools) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.Build,
                            contentDescription = "工具联动支持",
                            onClick = null
                        )
                    }
                    if (state.onOpenInfoDetail != null) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.Info,
                            contentDescription = "规格详情",
                            onClick = state.onOpenInfoDetail
                        )
                    }

                    if (state.onTest != null) {
                        data class TestIconMeta(
                            val icon: ImageVector,
                            val tint: Color,
                            val bg: Color,
                            val border: Color,
                            val desc: String
                        )

                        val meta = when (state.testStatus?.status) {
                            AppViewModel.ModelTestStatusKind.SUCCESS -> {
                                val latency = "${state.testStatus.latencyMs ?: 0}ms"
                                TestIconMeta(
                                    Icons.Outlined.CheckCircle,
                                    AppStatusColors.success,
                                    AppStatusColors.successContainer.copy(alpha = 0.65f),
                                    AppStatusColors.success.copy(alpha = 0.4f),
                                    "测试成功 ($latency)"
                                )
                            }
                            AppViewModel.ModelTestStatusKind.PENDING -> {
                                TestIconMeta(
                                    Icons.Outlined.Sync,
                                    AppStatusColors.warning,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    AppStatusColors.warning.copy(alpha = 0.4f),
                                    "测试中..."
                                )
                            }
                            AppViewModel.ModelTestStatusKind.ERROR -> {
                                TestIconMeta(
                                    Icons.Outlined.ErrorOutline,
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                    "测试失败"
                                )
                            }
                            null -> {
                                TestIconMeta(
                                    Icons.Outlined.Speed,
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    "测试连通性"
                                )
                            }
                        }

                        ActionSquareIcon(
                            icon = meta.icon,
                            contentDescription = meta.desc,
                            tint = meta.tint,
                            containerColor = meta.bg,
                            borderColor = meta.border,
                            onClick = state.onTest
                        )
                    }

                    ActionSquareIcon(
                        icon = if (state.isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (state.isEnabled) "已启用" else "已禁用",
                        tint = if (state.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        containerColor = if (state.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        borderColor = if (state.isEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        onClick = state.onToggleEnabled
                    )

                    if (state.onEdit != null) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            onClick = state.onEdit
                        )
                    }
                    if (state.onDelete != null) {
                        ActionSquareIcon(
                            icon = Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            onClick = state.onDelete
                        )
                    }
                }
            }

            if (state.reasoningVariants.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "推理等级",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.reasoningVariants.forEach { variant ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(AppStatusColors.success)
                                    )
                                    Text(
                                        text = variant,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "压缩策略",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.isCompressionCustom) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .clickable(onClick = state.onEditCompressionPolicy)
                        .padding(horizontal = 10.dp, vertical = 4.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.compressionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (state.isCompressionCustom) FontWeight.Medium else FontWeight.Normal
                            ),
                            color = if (state.isCompressionCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "编辑策略",
                            tint = if (state.isCompressionCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
