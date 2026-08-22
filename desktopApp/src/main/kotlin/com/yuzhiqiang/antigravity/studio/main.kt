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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

private object AppIconCache {
    val painter: Painter? by lazy {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/app-icon.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")
            if (stream != null) {
                BitmapPainter(loadImageBitmap(stream))
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

fun main() {
    // 强制开启 macOS Metal GPU 硬件加速与平滑渲染参数
    System.setProperty("skiko.renderApi", "METAL")
    System.setProperty("sun.java2d.metal", "true")
    System.setProperty("sun.java2d.opengl", "false")
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("apple.awt.fullscreencapturable", "true")
    System.setProperty("skiko.vsync", "true")

    application {
        val windowState = rememberWindowState(
            width = 1120.dp,
            height = 720.dp,
            position = WindowPosition(Alignment.Center)
        )
        var isVisible by remember { mutableStateOf(true) }
        val iconPainter = AppIconCache.painter

        if (iconPainter != null) {
            Tray(
                icon = iconPainter,
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
                icon = iconPainter
            ) {
                window.minimumSize = java.awt.Dimension(960, 640)

                App()
            }
        }
    }
}