package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

private enum class PolicyPresetMode {
    DEFAULT,
    PRESET_128K,
    PRESET_200K,
    PRESET_256K,
    PRESET_372K,
    PRESET_500K,
    PRESET_1M,
    CUSTOM
}

@Composable
fun PolicyEditorDialog(
    modelId: String,
    initialPolicy: ModelCompressionPolicy? = null,
    onDismiss: () -> Unit,
    onSave: (ModelCompressionPolicy?) -> Unit
) {
    val s = strings()

    var selectedMode by remember {
        mutableStateOf(
            when {
                initialPolicy == null -> PolicyPresetMode.DEFAULT
                initialPolicy.maxCheckpointTokens == 128_000L -> PolicyPresetMode.PRESET_128K
                initialPolicy.maxCheckpointTokens == 200_000L -> PolicyPresetMode.PRESET_200K
                initialPolicy.maxCheckpointTokens == 153_600L -> PolicyPresetMode.PRESET_256K
                initialPolicy.maxCheckpointTokens == 223_200L -> PolicyPresetMode.PRESET_372K
                initialPolicy.maxCheckpointTokens == 300_000L -> PolicyPresetMode.PRESET_500K
                initialPolicy.maxCheckpointTokens == 629_145L -> PolicyPresetMode.PRESET_1M
                else -> PolicyPresetMode.CUSTOM
            }
        )
    }

    var triggerThreshold by remember {
        mutableStateOf((initialPolicy?.triggerThresholdTokens ?: 152_000L).toString())
    }
    var maxCheckpoint by remember {
        mutableStateOf((initialPolicy?.maxCheckpointTokens ?: 200_000L).toString())
    }
    var reserveOutput by remember {
        mutableStateOf((initialPolicy?.reserveOutputTokens ?: 16_000L).toString())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(560.dp)
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
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppTokens.Size.iconLarge)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)) {
                        Text(
                            text = "上下文压缩策略配置",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "为 $modelId 设置 Checkpointer 上下文压缩触发与上限参数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 预设分段选择器
                Text(
                    text = "预设策略模式",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(AppTokens.Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xxs)
                ) {
                    listOf(
                        PolicyPresetMode.DEFAULT to "默认",
                        PolicyPresetMode.PRESET_128K to "128K",
                        PolicyPresetMode.PRESET_200K to "200K ★",
                        PolicyPresetMode.PRESET_256K to "256K",
                        PolicyPresetMode.PRESET_372K to "372K",
                        PolicyPresetMode.PRESET_500K to "500K",
                        PolicyPresetMode.PRESET_1M to "1M",
                        PolicyPresetMode.CUSTOM to "自定义"
                    ).forEach { (mode, label) ->
                        val isSelected = selectedMode == mode
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val txt = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AppTokens.Radius.pill))
                                .background(bg)
                                .clickable {
                                    selectedMode = mode
                                    when (mode) {
                                        PolicyPresetMode.DEFAULT -> {
                                            val p = ModelCompressionPolicy.preset200k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_128K -> {
                                            val p = ModelCompressionPolicy.preset128k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_200K -> {
                                            val p = ModelCompressionPolicy.preset200k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_256K -> {
                                            val p = ModelCompressionPolicy.preset256k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_372K -> {
                                            val p = ModelCompressionPolicy.preset372k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_500K -> {
                                            val p = ModelCompressionPolicy.preset500k()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        PolicyPresetMode.PRESET_1M -> {
                                            val p = ModelCompressionPolicy.preset1m()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        else -> {}
                                    }
                                }
                                .padding(vertical = AppTokens.Spacing.control),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = txt,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (selectedMode == PolicyPresetMode.DEFAULT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTokens.Radius.medium))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(AppTokens.Radius.medium))
                            .padding(AppTokens.Spacing.content)
                    ) {
                        Text(
                            text = "当前使用「默认策略」。系统不会覆盖自定义压缩阈值，遵循官方/上游推荐的上下文管理策略。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)) {
                        OutlinedTextField(
                            value = triggerThreshold,
                            onValueChange = {
                                triggerThreshold = it
                                selectedMode = PolicyPresetMode.CUSTOM
                            },
                            label = { Text("压缩触发阈值 (Tokens)") },
                            supportingText = { Text("当总上下文超过此阈值时触发自动滑动压缩") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppTokens.Radius.medium),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = maxCheckpoint,
                            onValueChange = {
                                maxCheckpoint = it
                                selectedMode = PolicyPresetMode.CUSTOM
                            },
                            label = { Text("Checkpoint 保留上限 (Tokens)") },
                            supportingText = { Text("压缩后最大允许保留的上下文历史量") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppTokens.Radius.medium),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = reserveOutput,
                            onValueChange = {
                                reserveOutput = it
                                selectedMode = PolicyPresetMode.CUSTOM
                            },
                            label = { Text("输出空间预留 (Tokens)") },
                            supportingText = { Text("为模型生成回答预留的安全 Token 预算") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppTokens.Radius.medium),
                            singleLine = true
                        )
                    }
                }

                // 底部按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(AppTokens.Radius.medium)
                    ) {
                        Text(s.commonCancel, style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            if (selectedMode == PolicyPresetMode.DEFAULT) {
                                onSave(null)
                            } else {
                                val basePolicy = initialPolicy ?: ModelCompressionPolicy()
                                val policy = basePolicy.copy(
                                    tokenThreshold = triggerThreshold.toLongOrNull() ?: 152_000L,
                                    maxTokenLimit = maxCheckpoint.toLongOrNull() ?: 200_000L,
                                    maxOutputTokens = reserveOutput.toLongOrNull() ?: 16_000L
                                )
                                onSave(policy)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(AppTokens.Radius.medium),
                        contentPadding = PaddingValues(horizontal = AppTokens.Spacing.card, vertical = AppTokens.Spacing.xs)
                    ) {
                        Text(s.commonSave, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
