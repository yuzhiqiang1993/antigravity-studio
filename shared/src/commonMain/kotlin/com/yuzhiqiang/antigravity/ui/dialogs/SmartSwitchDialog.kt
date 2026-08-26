package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzhiqiang.antigravity.domain.model.account.SmartSwitchConfig
import com.yuzhiqiang.antigravity.domain.model.account.SmartSwitchStrategy
import com.yuzhiqiang.antigravity.ui.components.StudioCard
import com.yuzhiqiang.antigravity.ui.components.StudioCheckbox
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

@Composable
fun SmartSwitchDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val smartConfig = config.smartSwitchConfig

    var enabled by remember(smartConfig) { mutableStateOf(smartConfig.enabled) }
    var thresholdPercent by remember(smartConfig) { mutableStateOf(smartConfig.triggerThresholdPercent.toFloat()) }
    var strategy by remember(smartConfig) { mutableStateOf(smartConfig.strategy) }
    var cooldownSeconds by remember(smartConfig) { mutableStateOf(smartConfig.cooldownSeconds.toFloat()) }
    var protectActiveGeneration by remember(smartConfig) { mutableStateOf(smartConfig.protectActiveGeneration) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioCard(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 540.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "自动智能切号策略",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "当额度耗尽或遭遇 429 报错时，自动无感调度最佳备用账号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Enable Switch Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "启用自动智能切号",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "遭遇 429 或配额见底时自动执行无感切号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                if (enabled) {
                    // Threshold Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("触发切号配额下限阈值", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${thresholdPercent.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = thresholdPercent,
                            onValueChange = { thresholdPercent = it },
                            valueRange = 1f..30f,
                            steps = 29
                        )
                    }

                    // Strategy Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("备用账号调度策略", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = strategy == SmartSwitchStrategy.HIGHEST_QUOTA_FIRST,
                                onClick = { strategy = SmartSwitchStrategy.HIGHEST_QUOTA_FIRST },
                                label = { Text(SmartSwitchStrategy.HIGHEST_QUOTA_FIRST.displayName) }
                            )
                            FilterChip(
                                selected = strategy == SmartSwitchStrategy.ROUND_ROBIN,
                                onClick = { strategy = SmartSwitchStrategy.ROUND_ROBIN },
                                label = { Text(SmartSwitchStrategy.ROUND_ROBIN.displayName) }
                            )
                        }
                    }

                    // Cooldown Slider
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("切号最小冷却间隔", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${cooldownSeconds.toInt()} 秒",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = cooldownSeconds,
                            onValueChange = { cooldownSeconds = it },
                            valueRange = 30f..300f,
                            steps = 9
                        )
                    }

                    // Active Generation Protection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("长对话生成加锁保护", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "流式生成或 Agent 执行期间禁止自动打断切号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StudioCheckbox(
                            checked = protectActiveGeneration,
                            onCheckedChange = { protectActiveGeneration = it }
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.updateSmartSwitchConfig(
                                SmartSwitchConfig(
                                    enabled = enabled,
                                    triggerThresholdPercent = thresholdPercent.toInt(),
                                    strategy = strategy,
                                    cooldownSeconds = cooldownSeconds.toInt(),
                                    protectActiveGeneration = protectActiveGeneration
                                )
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("保存策略配置")
                    }
                }
            }
        }
    }
}
