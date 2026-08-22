package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

/**
 * 推理档位与思考预算详情弹窗 (Material Design 3 规范)
 */
@Composable
fun ReasoningDetailDialog(
    modelName: String,
    reasoningLevels: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier
                    .padding(AppTokens.Spacing.card)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(AppTokens.Size.brandMark)
                                .clip(RoundedCornerShape(AppTokens.Radius.small))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppTokens.Size.iconLarge)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                            Text(
                                text = "深度思考与推理能力",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "该模型支持深度思考/推理模式。在与 IDE 协同对话时，模型可开启思考链，分析复杂逻辑与架构代码：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 档位列表
                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                    val displayLevels = if (reasoningLevels.isEmpty()) listOf("Default / Thinking") else reasoningLevels
                    displayLevels.forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppTokens.Radius.medium))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.medium))
                                .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.content),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(AppTokens.Size.statusDot)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                                Text(
                                    text = "档位: $level",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (level.lowercase()) {
                                        "high", "max" -> "高预算思考 (适合极度复杂的算法与重构方案)"
                                        "medium" -> "标准思考 (平衡推理深度与响应延迟)"
                                        "low" -> "轻量思考 (快速给出思考结论)"
                                        else -> "模型原生自适应深度思考"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text("知道了", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * 多模态输入详情弹窗
 */
@Composable
fun MultimodalDetailDialog(
    modelName: String,
    supportsVision: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier
                    .padding(AppTokens.Spacing.card)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(AppTokens.Size.brandMark)
                                .clip(RoundedCornerShape(AppTokens.Radius.small))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppTokens.Size.iconLarge)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                            Text(
                                text = "多模态输入支持",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "多模态能力允许模型直接理解视觉截图、设计图纸、架构图与代码引用：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                    ModalityItem(
                        icon = Icons.Outlined.Image,
                        title = "图像解析 (Vision)",
                        desc = "支持上传 PNG / JPEG / WEBP 设计图、UI 报错截图进行直接分析",
                        enabled = supportsVision
                    )
                    ModalityItem(
                        icon = Icons.Outlined.Description,
                        title = "文档理解 (Document)",
                        desc = "支持原生阅读 PDF / 文本规范文档并提取代码上下文",
                        enabled = true
                    )
                    ModalityItem(
                        icon = Icons.Outlined.Build,
                        title = "工具调用 (Function Calling)",
                        desc = "支持 IDE 工具自动化执行与终端命令联动",
                        enabled = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text("知道了", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalityItem(
    icon: ImageVector,
    title: String,
    desc: String,
    enabled: Boolean
) {
    val container = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val iconBg = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val iconTint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val textTitleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.Radius.medium))
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.medium))
            .padding(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.content),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(AppTokens.Radius.small))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(AppTokens.Size.iconMedium)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textTitleColor
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 模型上下文限制与元数据详情弹窗
 */
@Composable
fun ModelInfoDialog(
    modelName: String,
    modelId: String,
    contextLimit: Long?,
    outputLimit: Long?,
    roles: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 500.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(AppTokens.Radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = AppTokens.Elevation.dialog
        ) {
            Column(
                modifier = Modifier
                    .padding(AppTokens.Spacing.card)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(AppTokens.Size.brandMark)
                                .clip(RoundedCornerShape(AppTokens.Radius.small))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AppTokens.Size.iconLarge)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                            Text(
                                text = "模型规格与元数据",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppTokens.Size.iconLarge)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppTokens.Size.iconMedium)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius.medium))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.medium))
                        .padding(AppTokens.Spacing.card),
                    verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
                ) {
                    InfoRow("模型标识 (ID)", modelId, isMonospace = true)
                    InfoRow("上下文总窗口", if (contextLimit != null && contextLimit > 0) "${contextLimit / 1000}K Tokens (${contextLimit} tokens)" else "官方动态配置")
                    InfoRow("单次最大输出", if (outputLimit != null && outputLimit > 0) "${outputLimit / 1000}K Tokens (${outputLimit} tokens)" else "官方默认限制")
                    InfoRow("分配角色", if (roles.isNotEmpty()) roles.joinToString(", ") else "Agent, Code Assistance")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text("确定", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
