package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.components.StudioSlidingTabLayout
import com.yuzhiqiang.antigravity.ui.components.StudioTabItem

data class ProviderTabItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int
)

/**
 * 模型服务商 Tab 导航栏 (统一现代滑动药丸 TabLayout 模式)
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
        StudioSlidingTabLayout(
            items = tabItems,
            selectedKey = selectedId,
            onSelect = onSelect,
            modifier = Modifier.weight(1f, fill = false),
            tabHeight = 36.dp
        )

        if (trailingAction != null) {
            Spacer(Modifier.width(16.dp))
            trailingAction()
        }
    }
}
