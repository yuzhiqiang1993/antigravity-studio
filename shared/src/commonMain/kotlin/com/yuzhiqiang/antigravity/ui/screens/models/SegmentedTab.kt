package com.yuzhiqiang.antigravity.ui.screens.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

data class ProviderTabItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int
)

@Composable
fun ProviderTabLayout(
    items: List<ProviderTabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null
) {
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.section),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            },
            divider = {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200)
                )
                val badgeBgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    animationSpec = tween(200)
                )
                val badgeTextColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200)
                )

                Tab(
                    selected = isSelected,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.height(44.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 13.5.sp
                            ),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeBgColor)
                                .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = badgeTextColor
                            )
                        }
                    }
                }
            }
        }

        if (trailingAction != null) {
            trailingAction()
        }
    }
}
