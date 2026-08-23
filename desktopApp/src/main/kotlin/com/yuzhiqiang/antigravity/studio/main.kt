package com.yuzhiqiang.antigravity.studio

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yuzhiqiang.antigravity.di.appModule
import org.koin.core.context.startKoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.Taskbar
import javax.imageio.ImageIO

private object AppIconCache {
    private val isMac: Boolean
        get() = System.getProperty("os.name", "").lowercase().contains("mac")

    val appIconImage: java.awt.Image? by lazy {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
            if (stream != null) ImageIO.read(stream) else null
        } catch (_: Exception) {
            null
        }
    }

    val appIconPainter: Painter? by lazy {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
            if (stream != null) {
                BitmapPainter(loadImageBitmap(stream))
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // 托盘图标：macOS 使用专为菜单栏设计的模板图标 tray-icon-solid.png，其他平台使用彩色 tray-icon-solid-color.png
    val trayIconPainter: Painter? by lazy {
        try {
            val resName = if (isMac) "tray-icon-solid.png" else "tray-icon-solid-color.png"
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/$resName")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream(resName)
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")
            if (stream != null) {
                BitmapPainter(loadImageBitmap(stream))
            } else appIconPainter
        } catch (_: Exception) {
            appIconPainter
        }
    }
}

private fun setupPlatformAppIcon() {
    try {
        val icon = AppIconCache.appIconImage
        if (icon != null && Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = icon
            }
        }
    } catch (_: Exception) {
    }
}

fun main() {
    // macOS 原生 Skiko Metal 硬件加速与系统外观同步
    System.setProperty("skiko.renderApi", "METAL")
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("skiko.vsync", "true")

    startKoin {
        modules(appModule)
    }

    setupPlatformAppIcon()

    application {
        val windowState = rememberWindowState(
            width = 1120.dp,
            height = 720.dp,
            position = WindowPosition(Alignment.Center)
        )
        var isVisible by remember { mutableStateOf(true) }
        val appIcon = AppIconCache.appIconPainter
        val trayIcon = AppIconCache.trayIconPainter ?: appIcon

        if (trayIcon != null) {
            Tray(
                icon = trayIcon,
                tooltip = "Antigravity Studio",
                onAction = { isVisible = true },
                menu = {
                    Item("显示主窗口", onClick = { isVisible = true })
                    Separator()
                    Item("退出应用", onClick = ::exitApplication)
                }
            )
        }

        if (isVisible) {
            Window(
                onCloseRequest = { isVisible = false },
                state = windowState,
                title = "Antigravity Studio",
                icon = appIcon
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = java.awt.Dimension(960, 640)
                }

                App(window = window)
            }
        }
    }
}
