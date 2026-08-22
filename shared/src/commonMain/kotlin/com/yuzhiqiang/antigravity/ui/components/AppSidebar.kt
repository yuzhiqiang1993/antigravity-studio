package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab

private data class SidebarItem(
    val tab: NavTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun AppSidebar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val currentTab by viewModel.currentTab.collectAsState()
    val mainItems = listOf(
        SidebarItem(NavTab.OVERVIEW, s.navOverview, Icons.Outlined.Dashboard),
        SidebarItem(NavTab.MODELS, s.navModels, Icons.Outlined.Memory),
        SidebarItem(NavTab.ACTIVITY, s.navActivity, Icons.Outlined.Description)
    )
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .width(AppTokens.Size.sidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(size.width - strokeWidth / 2f, 0f),
                    end = Offset(size.width - strokeWidth / 2f, size.height),
                    strokeWidth = strokeWidth
                )
            }
            .padding(
                start = AppTokens.Spacing.content,
                end = AppTokens.Spacing.content,
                top = AppTokens.Size.sidebarTopPadding,
                bottom = AppTokens.Size.sidebarBottomPadding
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
        ) {
            BrandMark(modifier = Modifier.size(AppTokens.Size.brandMark))
            Text(
                text = "Antigravity Studio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(AppTokens.Spacing.section))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(AppTokens.Spacing.section))

        // Navigation Items
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)) {
            mainItems.forEach { item ->
                SidebarNavigationItem(
                    item = item,
                    selected = currentTab == item.tab,
                    onClick = { viewModel.selectTab(item.tab) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(AppTokens.Spacing.content))

        // Bottom Settings Tab
        SidebarNavigationItem(
            item = SidebarItem(NavTab.SETTINGS, s.navSettings, Icons.Outlined.Settings),
            selected = currentTab == NavTab.SETTINGS,
            onClick = { viewModel.selectTab(NavTab.SETTINGS) }
        )
    }
}

@Composable
private fun SidebarNavigationItem(
    item: SidebarItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerBg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppTokens.Size.navigationItemHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(containerBg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.content),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.control)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(AppTokens.Size.iconMedium)
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}
