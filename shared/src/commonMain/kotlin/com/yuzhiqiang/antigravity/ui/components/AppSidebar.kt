package com.yuzhiqiang.antigravity.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.i18n.strings
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import com.yuzhiqiang.antigravity.ui.presentation.NavTab
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

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
                start = AppTokens.Spacing.md,
                end = AppTokens.Spacing.md,
                top = AppTokens.Size.sidebarTopPadding,
                bottom = AppTokens.Size.sidebarBottomPadding
            )
    ) {
        // App Header Brand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.Spacing.xs, vertical = AppTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm)
        ) {
            BrandMark(size = AppTokens.Size.brandMark)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Antigravity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "Studio Hub",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(AppTokens.Spacing.lg))
        HorizontalDivider(color = dividerColor)
        Spacer(Modifier.height(AppTokens.Spacing.md))

        // Navigation Items
        Column(verticalArrangement = Arrangement.spacedBy(AppTokens.Spacing.xs)) {
            mainItems.forEach { item ->
                SidebarNavigationItem(
                    item = item,
                    selected = currentTab == item.tab,
                    onClick = { viewModel.selectTab(item.tab) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        HorizontalDivider(color = dividerColor)
        Spacer(Modifier.height(AppTokens.Spacing.sm))

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
    val containerBg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(200)
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppTokens.Size.navigationItemHeight)
            .clip(RoundedCornerShape(AppTokens.Radius.pill))
            .background(containerBg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.md)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(AppTokens.Size.navigationItemIconSize)
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}
