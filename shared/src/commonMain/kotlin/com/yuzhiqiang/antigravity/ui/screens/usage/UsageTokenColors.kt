package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.yuzhiqiang.antigravity.ui.theme.statusColors

/**
 * Token 维度的语义色。颜色来自 MaterialTheme，确保浅色/深色主题和动态配色可用。
 * cacheRead/cacheWrite 使用同一色相的不同强调度，保持与插件的缓存分组语义一致。
 */
@Immutable
data class UsageTokenColors(
    val input: Color,
    val cacheRead: Color,
    val cacheWrite: Color,
    val output: Color,
    val reasoning: Color
)

@Composable
fun usageTokenColors(): UsageTokenColors {
    val scheme = MaterialTheme.colorScheme
    val status = MaterialTheme.statusColors
    return UsageTokenColors(
        // 插件的语义顺序：蓝色输入、紫色缓存、绿色输出、琥珀色思考。
        // 使用当前主题的语义角色，保证桌面端自定义配色和深色模式仍可读。
        input = status.info,
        cacheRead = scheme.primary,
        cacheWrite = scheme.primary.copy(alpha = 0.62f),
        output = status.success,
        reasoning = status.warning
    )
}
