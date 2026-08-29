package com.yuzhiqiang.antigravity.ui.dialogs.doctor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.doctor.model.DoctorCheckItem
import com.yuzhiqiang.antigravity.doctor.model.DoctorFixAction

/**
 * 诊断分类区块卡片 (DoctorCategoryCard)：
 * - 顶部分离式简洁 Section 标题 (无死板黑竖线，保持现代轻量)
 * - 通透微灰底板容器 (surfaceVariant 0.45f)，与纯白弹窗外壳形成自然微层次
 * - 精致细分割线与 12dp 舒适圆角
 */
@Composable
fun DoctorCategoryCard(
    title: String,
    items: List<DoctorCheckItem>,
    onRunFix: (DoctorFixAction) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 简洁优雅 Section 标题
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // 条目容器面板 (微灰通透底衬)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    DoctorItemRow(item = item, onRunFix = onRunFix)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.20f else 0.30f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
