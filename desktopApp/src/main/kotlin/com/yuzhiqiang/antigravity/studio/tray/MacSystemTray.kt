package com.yuzhiqiang.antigravity.studio.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.yuzhiqiang.antigravity.i18n.I18nManager
import java.awt.MenuItem
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JDialog

/**
 * macOS 专属托盘实现：
 * 1. 解决 Compose 默认 Tray 无条件绑定 popupMenu 导致 macOS 状态栏拦截左键点击强行弹菜单的缺陷。
 * 2. 托盘不设置常驻 popupMenu，确保鼠标左键单击瞬间直接执行 [onAction] 激活并拉起主窗口。
 * 3. 鼠标右键单击（或双指点按/Control+左键）时在鼠标位置弹出 macOS 原生磨砂质感右键菜单，提供“显示主窗口”与“退出应用”。
 */
@Composable
internal fun MacSystemTray(
    icon: Painter,
    density: Density,
    tooltip: String,
    onAction: () -> Unit,
    onQuit: () -> Unit
) {
    if (!SystemTray.isSupported()) return

    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnQuit by rememberUpdatedState(onQuit)
    val s = I18nManager.strings

    val awtIcon = remember(icon, density) {
        icon.toAwtImage(density, LayoutDirection.Ltr, Size(22f, 22f))
    }

    DisposableEffect(awtIcon) {
        val systemTray = SystemTray.getSystemTray()
        val trayIcon = TrayIcon(awtIcon, tooltip).apply {
            isImageAutoSize = true
        }

        // 隐形 POPUP 窗口，作为右键触发时原生 AWT PopupMenu 的宿主组件
        val menuInvoker = JDialog().apply {
            isUndecorated = true
            type = java.awt.Window.Type.POPUP
            size = java.awt.Dimension(0, 0)
            opacity = 0f
        }

        val popupMenu = PopupMenu().apply {
            val showItem = MenuItem(s.trayShowMainWindow).apply {
                addActionListener { currentOnAction() }
            }
            val quitItem = MenuItem(s.trayQuitApplication).apply {
                addActionListener { currentOnQuit() }
            }
            add(showItem)
            addSeparator()
            add(quitItem)
        }
        menuInvoker.add(popupMenu)

        val mouseListener = object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                val isRightClick = e.button == MouseEvent.BUTTON3 ||
                    e.isPopupTrigger ||
                    (e.button == MouseEvent.BUTTON1 && e.isControlDown)
                if (isRightClick) {
                    try {
                        val p = MouseInfo.getPointerInfo()?.location ?: Point(e.xOnScreen, e.yOnScreen)
                        menuInvoker.location = p
                        menuInvoker.isVisible = true
                        popupMenu.show(menuInvoker, 0, 0)
                        menuInvoker.isVisible = false
                    } catch (_: Exception) {
                    }
                } else if (e.button == MouseEvent.BUTTON1) {
                    currentOnAction()
                }
            }
        }

        trayIcon.addMouseListener(mouseListener)
        // 兜底响应系统 ActionEvent
        trayIcon.addActionListener {
            currentOnAction()
        }

        try {
            systemTray.add(trayIcon)
        } catch (_: Exception) {
        }

        onDispose {
            try {
                systemTray.remove(trayIcon)
                menuInvoker.dispose()
            } catch (_: Exception) {
            }
        }
    }
}
