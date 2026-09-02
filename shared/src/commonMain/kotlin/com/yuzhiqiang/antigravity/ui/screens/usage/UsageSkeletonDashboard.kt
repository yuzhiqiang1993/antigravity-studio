package com.yuzhiqiang.antigravity.ui.screens.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzhiqiang.antigravity.ui.animation.rememberStudioShimmerBrush
import com.yuzhiqiang.antigravity.ui.animation.studioShimmer
import com.yuzhiqiang.antigravity.ui.components.StudioGlassCard
import com.yuzhiqiang.antigravity.ui.theme.AppTokens

import androidx.compose.ui.graphics.luminance
import com.yuzhiqiang.antigravity.ui.theme.StudioGlassTokens

/**
  * 用量统计页面高性能骨架加载大盘 (Usage Dashboard Skeleton)：
  * 1. 采用单一共享 Shimmer Brush，杜绝数百个独立的 Transition 造成的 CPU/GPU 满载与掉帧卡顿。
  * 2. 与真实大盘 5 大核心模块 1:1 镜像对齐，为用户提供丝滑顺畅的加载过渡体验。
  */
@Composable
fun UsageDashboardSkeleton(
    modifier: Modifier = Modifier
) {
    // 整个大盘共享同一个流光动画源，零冗余重绘与重组开销
    val shimmerBrush = rememberStudioShimmerBrush()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // A. Hero KPI 概览骨架
        UsageKpiGridSkeleton(brush = shimmerBrush)

        // B. 走势图骨架
        UsageTrendChartSkeleton(brush = shimmerBrush)

        // D. 热门模型排行与数据来源分布骨架
        TopModelsAndSourcesSkeleton(brush = shimmerBrush)
    }
}

/**
 * 1. Hero KPI 概览骨架卡片
 */
@Composable
fun UsageKpiGridSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
        borderColor = StudioGlassTokens.cleanBorderColor(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部 Hero 栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SkeletonBox(width = 46.dp, height = 46.dp, radius = 14.dp, brush = brush)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(width = 96.dp, height = 12.dp, brush = brush)
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SkeletonBox(width = 160.dp, height = 28.dp, brush = brush)
                            SkeletonBox(width = 72.dp, height = 14.dp, brush = brush)
                        }
                    }
                }

                // 右侧微卡片
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioGlassTokens.innerPanelBackgroundColor(isDark),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        StudioGlassTokens.innerPanelBorderColor(isDark)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(width = 40.dp, height = 10.dp, brush = brush)
                            SkeletonBox(width = 54.dp, height = 14.dp, brush = brush)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(width = 48.dp, height = 10.dp, brush = brush)
                            SkeletonBox(width = 60.dp, height = 14.dp, brush = brush)
                        }
                    }
                }
            }

            // 中层：输入 (Input)、输出 (Output)、缓存利用 (Cache) 三卡片骨架
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonDualCard(brush = brush, modifier = Modifier.weight(1f))
                SkeletonDualCard(brush = brush, modifier = Modifier.weight(1f))
                SkeletonDualCard(brush = brush, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonDualCard(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = StudioGlassTokens.innerPanelBackgroundColor(isDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            StudioGlassTokens.innerPanelBorderColor(isDark)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonBox(width = 15.dp, height = 15.dp, radius = 4.dp, brush = brush)
                    SkeletonBox(width = 110.dp, height = 14.dp, brush = brush)
                }
                SkeletonBox(width = 36.dp, height = 13.dp, brush = brush)
            }
            SkeletonBox(width = 100.dp, height = 24.dp, brush = brush)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBox(width = 70.dp, height = 11.dp, brush = brush)
                SkeletonBox(width = 70.dp, height = 11.dp, brush = brush)
            }
        }
    }
}

/**
 * 2. 消耗走势图骨架卡片
 */
@Composable
fun UsageTrendChartSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
        borderColor = StudioGlassTokens.cleanBorderColor(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonBox(width = 96.dp, height = 18.dp, brush = brush)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SkeletonBox(width = 8.dp, height = 8.dp, radius = 4.dp, brush = brush)
                                SkeletonBox(width = 42.dp, height = 11.dp, brush = brush)
                            }
                        }
                    }
                }
                SkeletonBox(width = 72.dp, height = 14.dp, brush = brush)
            }

            // 图表主体区域骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UsageVisualTokens.Chart.containerHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }

                // 中间流光图表占位
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(90.dp),
                    brush = brush,
                    radius = 8.dp
                )
            }

            // X 轴刻度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(7) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        SkeletonBox(width = 38.dp, height = 11.dp, brush = brush)
                        SkeletonBox(width = 24.dp, height = 11.dp, brush = brush)
                    }
                }
            }
        }
    }
}

/**
 * 3. 年度活跃度网格骨架卡片
 */
@Composable
fun ActivityHeatmapSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cellGap = 3.dp
    val cellSize = 11.5.dp

    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
        borderColor = StudioGlassTokens.cleanBorderColor(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(UsageVisualTokens.cardGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(width = 112.dp, height = 18.dp, brush = brush)
                SkeletonBox(width = 42.dp, height = 14.dp, brush = brush)
            }

            // 53 周网格骨架：共享 brush，超轻量渲染
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(cellGap)
            ) {
                repeat(48) {
                    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                        repeat(7) {
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .studioShimmer(brush = brush, shape = RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // 底部月份占位
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(8) {
                    SkeletonBox(width = 22.dp, height = 10.dp, brush = brush)
                }
            }
        }
    }
}

/**
 * 4. 热门模型与来源分布骨架卡片
 */
@Composable
fun TopModelsAndSourcesSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.weight(1.3f)) {
                    TopModelsBreakdownSkeleton(brush = brush)
                }
                Box(modifier = Modifier.weight(1f)) {
                    SourceBreakdownSkeleton(brush = brush)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TopModelsBreakdownSkeleton(brush = brush)
                SourceBreakdownSkeleton(brush = brush)
            }
        }
    }
}

@Composable
private fun TopModelsBreakdownSkeleton(brush: Brush) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    StudioGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
        borderColor = StudioGlassTokens.cleanBorderColor(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(width = 100.dp, height = 18.dp, brush = brush)
                SkeletonBox(width = 46.dp, height = 13.dp, brush = brush)
            }

            repeat(4) { index ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SkeletonBox(width = 22.dp, height = 22.dp, radius = 6.dp, brush = brush)
                            SkeletonBox(width = 120.dp, height = 14.dp, brush = brush)
                        }
                        SkeletonBox(width = 54.dp, height = 14.dp, brush = brush)
                    }
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        radius = 3.dp,
                        brush = brush
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(4) {
                            SkeletonBox(width = 48.dp, height = 11.dp, brush = brush)
                        }
                    }
                }
                if (index < 3) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBreakdownSkeleton(brush: Brush) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    StudioGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = StudioGlassTokens.cardBackgroundColor(isDark),
        borderColor = StudioGlassTokens.cleanBorderColor(isDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(width = 90.dp, height = 18.dp, brush = brush)

            repeat(3) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(width = 48.dp, height = 14.dp, brush = brush)
                        SkeletonBox(width = 80.dp, height = 13.dp, brush = brush)
                    }
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        radius = 3.dp,
                        brush = brush
                    )
                }
            }
        }
    }
}

/**
 * 5. 高消耗会话排行骨架卡片
 */
@Composable
fun TopConversationsSkeleton(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppTokens.Radius.medium),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(width = 110.dp, height = 18.dp, brush = brush)
                SkeletonBox(width = 48.dp, height = 13.dp, brush = brush)
            }

            repeat(3) { index ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SkeletonBox(width = 160.dp, height = 14.dp, brush = brush)
                            SkeletonBox(width = 38.dp, height = 16.dp, radius = 4.dp, brush = brush)
                        }
                        SkeletonBox(width = 60.dp, height = 14.dp, brush = brush)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(width = 110.dp, height = 11.dp, brush = brush)
                        SkeletonBox(width = 80.dp, height = 11.dp, brush = brush)
                    }
                }
                if (index < 2) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

/**
 * 基础骨架块工具组件，统一注入 studioShimmer 扫光动效
 */
@Composable
private fun SkeletonBox(
    width: Dp,
    height: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
    radius: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .studioShimmer(brush = brush, shape = RoundedCornerShape(radius))
    )
}

@Composable
private fun SkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    radius: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .studioShimmer(brush = brush, shape = RoundedCornerShape(radius))
    )
}
