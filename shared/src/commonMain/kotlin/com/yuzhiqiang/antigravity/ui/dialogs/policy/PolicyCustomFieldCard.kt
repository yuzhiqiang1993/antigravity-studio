package com.yuzhiqiang.antigravity.ui.dialogs.policy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.yuzhiqiang.antigravity.ui.components.StudioSegmentedControl
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 预设/默认模式下的指标卡片 (.policy-metric)
 */
@Composable
fun PolicyMetricCard(
    title: String,
    value: Long,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.30f else 0.50f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCommaNumber(value),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

/**
 * 自定义模式下的参数配置卡片 (.policy-custom-field)
 */
@Composable
fun PolicyCustomFieldCard(
    title: String,
    badgeText: String,
    isPercentMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modeTabLabels: Pair<String, String>,
    rawInputValue: String,
    onValueChange: (String) -> Unit,
    percentContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.30f else 0.50f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header (.policy-field-header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // 2. 输入模式切换分段器 (.policy-input-mode-segmented)
            val modeItems = remember(modeTabLabels) {
                listOf(
                    StudioTabItem(true, modeTabLabels.first),
                    StudioTabItem(false, modeTabLabels.second)
                )
            }
            StudioSegmentedControl(
                items = modeItems,
                selectedKey = isPercentMode,
                onSelect = onModeChange,
                modifier = Modifier.fillMaxWidth(),
                height = 26.dp
            )

            // 3. 内容区：等高容器 (height = 60.dp) 保证三张卡片底部绝对平齐！
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPercentMode) {
                    percentContent()
                } else {
                    PolicyInputWrapper(
                        value = rawInputValue,
                        onValueChange = onValueChange
                    )
                }
            }
        }
    }
}

/**
 * 百分比选择网格 (4 列排布，共 7~8 个药丸)
 */
@Composable
fun PolicyPercentageGrid(
    percentages: List<Int>,
    baseCapacity: Long,
    currentValue: Long,
    onSelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 第一行 4 个
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            percentages.take(4).forEach { pct ->
                val calcVal = (baseCapacity * (pct / 100.0)).toLong()
                val isMatch = Math.abs(currentValue - calcVal) <= (baseCapacity * 0.025)
                PolicyPercentagePill(
                    text = "$pct%",
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(calcVal) }
                )
            }
        }

        // 第二行剩余项
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            percentages.drop(4).forEach { pct ->
                val calcVal = (baseCapacity * (pct / 100.0)).toLong()
                val isMatch = Math.abs(currentValue - calcVal) <= (baseCapacity * 0.025)
                PolicyPercentagePill(
                    text = "$pct%",
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(calcVal) }
                )
            }
            // 占位补齐
            if (percentages.drop(4).size < 4) {
                Spacer(modifier = Modifier.weight((4 - percentages.drop(4).size).toFloat()))
            }
        }
    }
}

/**
 * 预设备选网格 (2 列排布，共 4 个药丸)
 */
@Composable
fun PolicyReserveGrid(
    reserves: List<Pair<Long, String>>,
    currentValue: Long,
    onSelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reserves.take(2).forEach { (valTokens, label) ->
                val isMatch = currentValue == valTokens
                PolicyPercentagePill(
                    text = label,
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(valTokens) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            reserves.drop(2).forEach { (valTokens, label) ->
                val isMatch = currentValue == valTokens
                PolicyPercentagePill(
                    text = label,
                    isSelected = isMatch,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(valTokens) }
                )
            }
        }
    }
}

/**
 * 精致百分比药丸按钮 (.policy-percentage-btn)
 */
@Composable
fun PolicyPercentagePill(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else Color.White
    val borderCol = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(27.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textCol
            ),
            maxLines = 1
        )
    }
}

/**
 * 单行输入框封装 (.policy-input-wrapper)
 */
@Composable
fun PolicyInputWrapper(
    value: String,
    onValueChange: (String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerLow else Color.White)
                    .border(
                        1.dp,
                        if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    innerTextField()
                }
                Text(
                    text = "Tokens",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    )
}
