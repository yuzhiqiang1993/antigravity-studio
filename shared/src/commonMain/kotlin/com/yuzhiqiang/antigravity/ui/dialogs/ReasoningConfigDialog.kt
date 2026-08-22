package com.yuzhiqiang.antigravity.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuzhiqiang.antigravity.domain.model.ProviderProtocol
import com.yuzhiqiang.antigravity.domain.model.ReasoningLevel
import com.yuzhiqiang.antigravity.domain.model.ReasoningMappingSupport

/** 配置单个上游模型 reasoning 档位、custom mapping 与 Gemini 思考预算。 */
@Composable
fun ReasoningConfigDialog(
    modelName: String,
    protocol: ProviderProtocol,
    outputTokenLimit: Long?,
    initialDraft: ReasoningConfigDraft,
    onDismiss: () -> Unit,
    onConfirm: (ReasoningConfigDraft) -> Unit
) {
    var enabled by remember { mutableStateOf(initialDraft.enabled) }
    var selectedLevels by remember { mutableStateOf(initialDraft.levels) }
    var customValue by remember { mutableStateOf(initialDraft.customValue.orEmpty()) }
    var thinkingBudget by remember { mutableStateOf(initialDraft.thinkingBudget?.toString().orEmpty()) }
    var minThinkingBudget by remember { mutableStateOf(initialDraft.minThinkingBudget?.toString().orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectableLevels = remember(protocol, outputTokenLimit, initialDraft) {
        val candidates = if (
            initialDraft.levels.isEmpty() &&
            initialDraft.mappings.isEmpty() &&
            (initialDraft.thinkingBudget != null || initialDraft.minThinkingBudget != null)
        ) {
            emptyList()
        } else {
            ReasoningMappingSupport.defaultLevels(protocol)
        }
        (candidates + initialDraft.levels).distinct().filter { level ->
            val configured = initialDraft.mappings[level]
            configured?.let { mapping ->
                ReasoningMappingSupport.isSupported(protocol, mapping, outputTokenLimit)
            } ?: ReasoningMappingSupport.defaultMapping(protocol, level)?.let { mapping ->
                ReasoningMappingSupport.isSupported(protocol, mapping, outputTokenLimit)
            } == true
        }
    }

    fun parseBudget(value: String, label: String): Int? {
        if (value.isBlank()) return null
        val parsed = value.toIntOrNull()
        if (parsed == null) {
            validationError = "${label}必须是整数"
            return null
        }
        return parsed
    }

    fun confirm() {
        validationError = null
        if (!enabled) {
            onConfirm(
                ReasoningConfigDraft(
                    enabled = false,
                    levels = emptySet(),
                    customValue = null,
                    thinkingBudget = null,
                    minThinkingBudget = null,
                    mappings = emptyMap(),
                    configuredSupported = false
                )
            )
            return
        }
        val budget = parseBudget(thinkingBudget, "思考预算")
        if (validationError != null) return
        val minBudget = parseBudget(minThinkingBudget, "最小思考预算")
        if (validationError != null) return
        if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
            if (budget != null && budget < -1) {
                validationError = "Gemini 思考预算只能为 -1、0 或正整数"
                return
            }
            if (minBudget != null && minBudget <= 0) {
                validationError = "最小思考预算必须大于 0"
                return
            }
            if (budget != null && budget > 0 && minBudget != null && minBudget > budget) {
                validationError = "最小思考预算不能大于思考预算"
                return
            }
        }
        if (protocol != ProviderProtocol.GEMINI_GENERATE_CONTENT && (budget != null || minBudget != null)) {
            validationError = "只有 Gemini 协议支持模型级思考预算"
            return
        }
        val custom = customValue.trim().takeIf { it.isNotEmpty() }
        if (custom != null && ReasoningMappingSupport.customMapping(protocol, custom, outputTokenLimit) == null) {
            validationError = "Custom reasoning 值不符合当前协议或输出上限约束"
            return
        }
        val invalidLevel = selectedLevels.firstOrNull { level ->
            val mapping = ReasoningMappingSupport.resolveMapping(
                protocol = protocol,
                level = level,
                configured = initialDraft.mappings,
                outputTokenLimit = outputTokenLimit
            )
            mapping == null
        }
        if (invalidLevel != null) {
            validationError = "推理档位 ${invalidLevel.label} 不符合当前协议或输出上限约束"
            return
        }
        if (selectedLevels.isEmpty() && custom == null && budget == null && minBudget == null) {
            validationError = "请至少选择一个推理档位，或填写思考预算"
            return
        }
        onConfirm(
            initialDraft.copy(
                enabled = true,
                levels = selectedLevels,
                customValue = custom,
                thinkingBudget = budget,
                minThinkingBudget = minBudget
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("配置深度思考", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(modelName, fontSize = 12.sp, color = Color(0xFF64748B))
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enabled, onCheckedChange = { value ->
                    enabled = value
                    validationError = null
                })
                Text("启用 reasoning", fontSize = 13.sp)
            }
            if (enabled) {
                Text("可用推理档位", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                selectableLevels.forEach { level ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = level in selectedLevels,
                            onCheckedChange = { checked ->
                                selectedLevels = if (checked) {
                                    selectedLevels + level
                                } else {
                                    selectedLevels - level
                                }
                                validationError = null
                            }
                        )
                        Text(level.label, fontSize = 12.5.sp)
                    }
                }
                OutlinedTextField(
                    value = customValue,
                    onValueChange = { value ->
                        customValue = value
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Custom reasoning 值（可选）") },
                    placeholder = {
                        Text(
                            when (protocol) {
                                ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                                ProviderProtocol.OPENAI_RESPONSES -> "例如 xhigh"

                                ProviderProtocol.ANTHROPIC_MESSAGES -> "例如 adaptive 或 16384"
                                ProviderProtocol.GEMINI_GENERATE_CONTENT -> "例如 high 或 8192"
                            }
                        )
                    }
                )
                if (protocol == ProviderProtocol.GEMINI_GENERATE_CONTENT) {
                    OutlinedTextField(
                        value = thinkingBudget,
                        onValueChange = { value ->
                            thinkingBudget = value
                            validationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("默认思考预算（可选）") },
                        placeholder = { Text("-1 表示动态预算") }
                    )
                    OutlinedTextField(
                        value = minThinkingBudget,
                        onValueChange = { value ->
                            minThinkingBudget = value
                            validationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("最小思考预算（可选）") }
                    )
                }
                validationError?.let { error ->
                    Text(error, color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = ::confirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("确认")
                }
            }
        }
    }
}
