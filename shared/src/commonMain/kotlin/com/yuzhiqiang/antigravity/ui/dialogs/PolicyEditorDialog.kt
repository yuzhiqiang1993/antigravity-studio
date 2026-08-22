package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ModelCompressionPolicy
import com.yuzhiqiang.antigravity.i18n.strings

private enum class PolicyPresetMode {
    DEFAULT,
    PRESET_128K,
    PRESET_200K,
    PRESET_256K,
    PRESET_372K,
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
                initialPolicy.maxCheckpointTokens == 256_000L -> PolicyPresetMode.PRESET_256K
                initialPolicy.maxCheckpointTokens == 372_000L -> PolicyPresetMode.PRESET_372K
                initialPolicy.maxCheckpointTokens == 1_000_000L -> PolicyPresetMode.PRESET_1M
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
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "上下文压缩策略配置",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "为 $modelId 设置 Checkpointer 上下文压缩触发与上限参数",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 预设分段选择器 (对齐 .policy-preset-segmented)
                Text(
                    text = "预设策略模式",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        PolicyPresetMode.DEFAULT to "默认",
                        PolicyPresetMode.PRESET_128K to "128K",
                        PolicyPresetMode.PRESET_200K to "200K ★",
                        PolicyPresetMode.PRESET_256K to "256K",
                        PolicyPresetMode.PRESET_372K to "372K",
                        PolicyPresetMode.PRESET_1M to "1M",
                        PolicyPresetMode.CUSTOM to "自定义"
                    ).forEach { (mode, label) ->
                        val isSelected = selectedMode == mode
                        val bg = if (isSelected) Color.White else Color.Transparent
                        val txt = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(bg)
                                .clickable {
                                    selectedMode = mode
                                    when (mode) {
                                        PolicyPresetMode.DEFAULT -> {
                                            // 默认模式使用标准基线
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
                                        PolicyPresetMode.PRESET_1M -> {
                                            val p = ModelCompressionPolicy.preset1m()
                                            triggerThreshold = p.triggerThresholdTokens.toString()
                                            maxCheckpoint = p.maxCheckpointTokens.toString()
                                            reserveOutput = p.reserveOutputTokens.toString()
                                        }
                                        else -> {}
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "当前使用「默认策略」。系统不会覆盖自定义压缩阈值，遵循官方/上游推荐的上下文管理策略。",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 17.sp
                        )
                    }
                } else {
                    // 详细字段输入
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = triggerThreshold,
                            onValueChange = {
                                triggerThreshold = it
                                selectedMode = PolicyPresetMode.CUSTOM
                            },
                            label = { Text("压缩触发阈值 (Tokens)") },
                            supportingText = { Text("当总上下文超过此阈值时触发自动滑动压缩") },
                            modifier = Modifier.fillMaxWidth(),
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
                    TextButton(onClick = onDismiss) {
                        Text(s.commonCancel)
                    }

                    Button(
                        onClick = {
                            if (selectedMode == PolicyPresetMode.DEFAULT) {
                                onSave(null)
                            } else {
                                val policy = ModelCompressionPolicy(
                                    triggerThresholdTokens = triggerThreshold.toLongOrNull() ?: 152_000L,
                                    maxCheckpointTokens = maxCheckpoint.toLongOrNull() ?: 200_000L,
                                    reserveOutputTokens = reserveOutput.toLongOrNull() ?: 16_000L
                                )
                                onSave(policy)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text(s.commonSave)
                    }
                }
            }
        }
    }
}
