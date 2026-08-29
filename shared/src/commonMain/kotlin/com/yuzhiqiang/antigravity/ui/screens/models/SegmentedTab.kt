package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem
import com.yuzhiqiang.antigravity.ui.components.StudioUnderlineTabLayout

data class ProviderTabItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int
)

/**
 * 模型服务商 Tab 导航栏 (对标 JetBrains Toolbox 经典下划线导航模式)：
 * - 极简通透无实心底槽
 * - 纯正深海蓝 2.5dp 弹性滑动高光指示横线
 * - 舒适的左右留白与呼吸感
 */
@Composable
fun ProviderTabLayout(
    items: List<ProviderTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null
) {
    val tabItems = remember(items) {
        items.map { item ->
            StudioTabItem(
                key = item.id,
                title = item.title,
                icon = item.icon,
                badge = item.count.toString()
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioUnderlineTabLayout(
            items = tabItems,
            selectedKey = selectedId,
            onSelect = onSelect,
            modifier = Modifier.weight(1f, fill = false),
            tabHeight = 42.dp
        )

        if (trailingAction != null) {
            Spacer(Modifier.width(16.dp))
            trailingAction()
        }
    }
}
