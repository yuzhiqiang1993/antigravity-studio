package com.yuzhiqiang.antigravity.studio

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
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
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Desktop
import java.awt.Frame
import java.awt.Taskbar
import java.awt.desktop.AppReopenedListener
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

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

    fun loadTraySvgPainter(density: Density): Painter? {
        val resourceName = if (isMac) "tray-icon-solid-mac.svg" else "tray-icon-solid.svg"
        val stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourceName)
            ?: return null
        return try {
            stream.use { loadSvgPainter(it, density) }
        } catch (_: Exception) {
            null
        }
    }
}

private fun setupPlatformAppIcon() {
    try {
        val isMac = System.getProperty("os.name", "").lowercase().contains("mac")
        // macOS 下由系统原生 Bundle (icon.icns) 负责 Dock 栏圆角遮罩与渲染；
        // 运行时调用 Taskbar.iconImage 会以直角位图强制覆盖，破坏 macOS 原生的 Squircle 视觉规范。
        if (!isMac) {
            val icon = AppIconCache.appIconImage
            if (icon != null && Taskbar.isTaskbarSupported()) {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.iconImage = icon
                }
            }
        }
    } catch (_: Exception) {
    }
}

fun main() {
    val osName = System.getProperty("os.name", "").lowercase()
    if (osName.contains("mac")) {
        // macOS 原生 Skiko Metal 硬件加速与系统外观同步
        System.setProperty("skiko.renderApi", "METAL")
        System.setProperty("apple.awt.application.appearance", "system")
    } else if (osName.contains("win")) {
        // Windows 原生 Direct3D 加速
        System.setProperty("skiko.renderApi", "DIRECT3D")
    }
    System.setProperty("skiko.vsync", "true")

    startKoin {
        modules(appModule)
    }

    setupPlatformAppIcon()

    application {
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 820.dp,
            position = WindowPosition(Alignment.Center)
        )
        var isVisible by remember { mutableStateOf(true) }
        var windowRef by remember { mutableStateOf<ComposeWindow?>(null) }
        val appIcon = AppIconCache.appIconPainter
        val density = LocalDensity.current
        val trayIcon = remember(density) {
            AppIconCache.loadTraySvgPainter(density)
                ?: AppIconCache.trayIconPainter
                ?: appIcon
        }

        val showAndFocusWindow = remember {
            {
                isVisible = true
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().requestForeground(true)
                    }
                } catch (_: Exception) {
                }
                SwingUtilities.invokeLater {
                    windowRef?.let { win ->
                        if ((win.extendedState and Frame.ICONIFIED) != 0) {
                            win.extendedState = win.extendedState and Frame.ICONIFIED.inv()
                        }
                        win.isVisible = true
                        win.toFront()
                        win.requestFocus()
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            val listener = AppReopenedListener {
                showAndFocusWindow()
            }
            try {
                if (Desktop.isDesktopSupported()) {
                    val desktop = Desktop.getDesktop()
                    if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                        desktop.addAppEventListener(listener)
                    }
                }
            } catch (_: Exception) {
            }
            onDispose {
                try {
                    if (Desktop.isDesktopSupported()) {
                        val desktop = Desktop.getDesktop()
                        if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
                            desktop.removeAppEventListener(listener)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (trayIcon != null) {
            val s = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
            Tray(
                icon = trayIcon,
                tooltip = s.appName,
                onAction = showAndFocusWindow,
                menu = {
                    Item(s.trayShowMainWindow, onClick = showAndFocusWindow)
                    Separator()
                    Item(s.trayQuitApplication, onClick = ::exitApplication)
                }
            )
        }

        Window(
            visible = isVisible,
            onCloseRequest = { isVisible = false },
            state = windowState,
            title = "Antigravity Studio",
            icon = appIcon
        ) {
            DisposableEffect(window) {
                windowRef = window
                SwingUtilities.invokeLater {
                    if ((window.extendedState and Frame.ICONIFIED) != 0) {
                        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                    }
                    window.toFront()
                    window.requestFocus()
                }
                onDispose {
                }
            }

            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(1020, 680)
            }

            App(window = window)
        }
    }
}
