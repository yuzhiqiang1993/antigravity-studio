package com.yuzhiqiang.antigravity.studio.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray

/**
 * 跨平台应用系统托盘统一入口：
 * - macOS：调用 [MacSystemTray]，解除系统强制菜单拦截，实现左键直接拉起、右键原生毛玻璃菜单；
 * - Windows：调用 [WindowsSystemTray]，解决 Win32 乱码，左键直接拉起、右键自绘 Fluent 风格自适应菜单；
 * - 其他平台（如 Linux）：调用 Compose 原生 [Tray] 并展示系统菜单。
 */
@Composable
fun ApplicationScope.AppTray(
    trayIcon: Painter,
    density: Density,
    appName: String,
    showText: String,
    quitText: String,
    onAction: () -> Unit,
    onQuit: () -> Unit
) {
    val osName = remember { System.getProperty("os.name", "").lowercase() }

    when {
        osName.contains("mac") -> {
            MacSystemTray(
                icon = trayIcon,
                density = density,
                tooltip = appName,
                onAction = onAction,
                onQuit = onQuit
            )
        }
        osName.contains("win") -> {
            WindowsSystemTray(
                icon = trayIcon,
                tooltip = appName,
                appName = appName,
                showText = showText,
                quitText = quitText,
                onAction = onAction,
                onQuit = onQuit
            )
        }
        else -> {
            Tray(
                icon = trayIcon,
                tooltip = appName,
                onAction = onAction,
                menu = {
                    Item(showText, onClick = onAction)
                    Separator()
                    Item(quitText, onClick = onQuit)
                }
            )
        }
    }
}
