package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenu
import com.yuzhiqiang.antigravity.ui.components.StudioDropdownMenuItem
import com.yuzhiqiang.antigravity.ui.components.StudioMenuDivider
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppFeatureColors
import com.yuzhiqiang.antigravity.ui.theme.AppStatusColors
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UniversalModelCard(
    state: UniversalModelCardUiState,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val featureColors = AppFeatureColors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardAlpha by animateFloatAsState(
        targetValue = if (state.isEnabled) 1f else 0.55f,
        animationSpec = tween(AppTokens.Motion.durationMedium)
    )

    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !state.isEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            isHovered -> state.brand.colors.accent.copy(alpha = 0.45f)
            else -> StudioGlassTokens.cleanBorderColor(isDark, isHovered)
        },
        animationSpec = tween(150)
    )

    val cardBg = StudioGlassTokens.cardBackgroundColor(isDark)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .border(
                width = StudioGlassTokens.borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(StudioGlassTokens.cardCornerRadius)
            )
            .hoverable(interactionSource),
        shape = RoundedCornerShape(StudioGlassTokens.cardCornerRadius),
        color = cardBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Brand Icon, Title, Model ID & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 品牌微胶囊图标
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = state.brand.colors.accent.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = state.brand.iconVector,
                                contentDescription = state.brand.brandName,
                                tint = state.brand.colors.accent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!state.isEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = s.modelsDisabledDesc,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = state.subtitle ?: state.brand.brandName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 右侧操作栏 (Toolbox 风格收敛：测速 + 显隐切换 + 更多菜单 ⋮)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 测速状态/按钮
                    if (state.onTest != null) {
                        val testMeta = when (state.testStatus?.status) {
                            AppViewModel.ModelTestStatusKind.SUCCESS -> {
                                val latency = "${state.testStatus.latencyMs ?: 0}ms"
                                Triple(
                                    Icons.Outlined.CheckCircle,
                                    AppStatusColors.success,
                                    s.modelsTestSuccess(latency)
                                )
                            }
                            AppViewModel.ModelTestStatusKind.PENDING -> {
                                Triple(
                                    Icons.Outlined.Sync,
                                    AppStatusColors.warning,
                                    s.modelsTesting
                                )
                            }
                            AppViewModel.ModelTestStatusKind.ERROR -> {
                                Triple(
                                    Icons.Outlined.ErrorOutline,
                                    MaterialTheme.colorScheme.error,
                                    s.modelsTestFailed
                                )
                            }
                            null -> {
                                Triple(
                                    Icons.Outlined.Speed,
                                    MaterialTheme.colorScheme.primary,
                                    s.modelsTestConnection
                                )
                            }
                        }

                        ActionSquareIcon(
                            icon = testMeta.first,
                            contentDescription = testMeta.third,
                            tint = testMeta.second,
                            containerColor = testMeta.second.copy(alpha = 0.08f),
                            borderColor = testMeta.second.copy(alpha = 0.28f),
                            onClick = state.onTest
                        )
                    }

                    // 启用/停用切换
                    ActionSquareIcon(
                        icon = if (state.isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (state.isEnabled) s.modelsEnabledDesc else s.modelsDisabledDesc,
                        tint = if (state.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        containerColor = if (state.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        borderColor = if (state.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        onClick = state.onToggleEnabled
                    )

                    // 更多操作下拉菜单 ⋮ (详情/编辑/删除)
                    var showMoreMenu by remember { mutableStateOf(false) }
                    val hasMoreActions = state.onOpenInfoDetail != null || state.onEdit != null || state.onDelete != null

                    if (hasMoreActions) {
                        Box {
                            ActionSquareIcon(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = s.modelsSpecsDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { showMoreMenu = true }
                            )

                            StudioDropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                if (state.onOpenInfoDetail != null) {
                                    StudioDropdownMenuItem(
                                        text = s.modelsSpecsDesc,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            state.onOpenInfoDetail.invoke()
                                        }
                                    )
                                }

                                if (state.onEdit != null) {
                                    StudioDropdownMenuItem(
                                        text = s.modelsEditModel,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            state.onEdit.invoke()
                                        }
                                    )
                                }

                                if (state.onDelete != null) {
                                    StudioMenuDivider()
                                    StudioDropdownMenuItem(
                                        text = s.modelsDeleteModel,
                                        isDestructive = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            state.onDelete.invoke()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Capabilities Section: 多模态、工具调用、思考推理、上下文规格
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. 多模态 (Multimodal)
                if (state.supportsVision) {
                    CapabilityPill(
                        icon = Icons.Outlined.AutoAwesome,
                        label = s.modelsVision,
                        containerColor = featureColors.vision.container,
                        contentColor = featureColors.vision.foreground,
                        borderColor = featureColors.vision.border,
                        onClick = state.onOpenVisionDetail
                    )
                }

                // 2. 工具调用 (Tools)
                if (state.supportsTools) {
                    CapabilityPill(
                        icon = Icons.Outlined.Build,
                        label = s.modelsTools,
                        containerColor = featureColors.tools.container,
                        contentColor = featureColors.tools.foreground,
                        borderColor = featureColors.tools.border,
                        onClick = null
                    )
                }

                // 3. 思考推理 (Reasoning)
                if (state.supportsReasoning || state.reasoningVariants.isNotEmpty()) {
                    CapabilityPill(
                        icon = Icons.Outlined.Psychology,
                        label = s.modelsReasoningLevelLabel,
                        containerColor = featureColors.reasoning.container,
                        contentColor = featureColors.reasoning.foreground,
                        borderColor = featureColors.reasoning.border,
                        onClick = state.onOpenReasoningDetail
                    )
                }

                // 4. 上下文规格 (Context Window)
                val contextLabel = buildString {
                    append(state.contextLimitText)
                    if (!state.outputLimitText.isNullOrBlank()) {
                        append(" · ")
                        append(state.outputLimitText)
                    }
                }
                if (contextLabel.isNotBlank()) {
                    CapabilityPill(
                        icon = Icons.Outlined.DataObject,
                        label = contextLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        onClick = state.onOpenInfoDetail
                    )
                }
            }

            // Reasoning Level Variants (推理档位指示)
            if (state.reasoningVariants.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = s.modelsReasoningLevelLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.reasoningVariants.forEach { variant ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.5.dp)
                                            .clip(CircleShape)
                                            .background(AppStatusColors.success)
                                    )
                                    Text(
                                        text = variant,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
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

            // Footer: Compression Policy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.modelsCompressionPolicyLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (state.isCompressionCustom) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (state.isCompressionCustom) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = state.onEditCompressionPolicy)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = state.compressionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (state.isCompressionCustom) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (state.isCompressionCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = s.modelsEditPolicy,
                            tint = if (state.isCompressionCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 语义化能力胶囊 Chip 组件
 */
@Composable
private fun CapabilityPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(7.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedContainer by animateColorAsState(
        targetValue = if (isHovered && onClick != null) {
            containerColor.copy(alpha = (containerColor.alpha * 1.5f).coerceAtMost(0.85f))
        } else {
            containerColor
        },
        animationSpec = tween(150)
    )

    val animatedBorder by animateColorAsState(
        targetValue = if (isHovered && onClick != null) {
            borderColor.copy(alpha = (borderColor.alpha * 1.5f).coerceAtMost(0.95f))
        } else {
            borderColor
        },
        animationSpec = tween(150)
    )

    Surface(
        shape = shape,
        color = animatedContainer,
        border = BorderStroke(1.dp, animatedBorder),
        modifier = Modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
