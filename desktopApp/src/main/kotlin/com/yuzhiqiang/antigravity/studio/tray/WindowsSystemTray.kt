package com.yuzhiqiang.antigravity.studio.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.border.EmptyBorder

/**
 * Windows 专属托盘实现：
 * 1. 彻底解决 Windows 下 AWT PopupMenu / MenuItem 的 Win32 中文字符编码乱码（???）顽疾。
 * 2. 避免常驻弹出菜单拦截左键单击，左键点击直接拉起并聚焦主窗口。
 * 3. 鼠标右键单击时弹出自绘 Fluent 风格卡片菜单，支持深/浅色主题自适应、微阴影、圆角、悬停特效与边缘贴合防溢出。
 */
@Composable
internal fun ApplicationScope.WindowsSystemTray(
    icon: Painter,
    tooltip: String,
    appName: String,
    showText: String,
    quitText: String,
    onAction: () -> Unit,
    onQuit: () -> Unit
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnQuit by rememberUpdatedState(onQuit)

    Tray(
        icon = icon,
        tooltip = tooltip,
        onAction = currentOnAction
    )

    DisposableEffect(icon) {
        val systemTray = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null
        val installedTrayIcon = systemTray?.trayIcons?.lastOrNull()
        installedTrayIcon?.popupMenu = null

        val menuDialog = JDialog().apply {
            isUndecorated = true
            background = Color(0, 0, 0, 0)
            isAlwaysOnTop = true
            type = java.awt.Window.Type.POPUP
            focusableWindowState = true
            addWindowFocusListener(object : WindowFocusListener {
                override fun windowLostFocus(e: WindowEvent) {
                    isVisible = false
                }
                override fun windowGainedFocus(e: WindowEvent) = Unit
            })
            val escapeKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
            rootPane.registerKeyboardAction(
                { isVisible = false },
                escapeKey,
                JComponent.WHEN_IN_FOCUSED_WINDOW
            )
        }

        var lastActionTime = 0L
        val mouseListener = object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && !e.isPopupTrigger) {
                    val now = System.currentTimeMillis()
                    if (now - lastActionTime > 200L) {
                        lastActionTime = now
                        menuDialog.isVisible = false
                        currentOnAction()
                    }
                } else if (e.button == MouseEvent.BUTTON3 || e.isPopupTrigger) {
                    val mousePoint = try {
                        MouseInfo.getPointerInfo()?.location ?: Point(e.x, e.y)
                    } catch (_: Exception) {
                        Point(e.x, e.y)
                    }
                    val mouseX = mousePoint.x
                    val mouseY = mousePoint.y

                    val isDark = ModernTrayMenu.isWindowsSystemDark()
                    val panel = ModernTrayMenu.createMenuPanel(
                        appName = appName,
                        showText = showText,
                        quitText = quitText,
                        isDark = isDark,
                        onShow = {
                            menuDialog.isVisible = false
                            currentOnAction()
                        },
                        onQuit = {
                            menuDialog.isVisible = false
                            currentOnQuit()
                        }
                    )

                    menuDialog.contentPane = panel
                    menuDialog.pack()
                    val prefSize = menuDialog.preferredSize

                    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    val screenDevice = ge.screenDevices.firstOrNull { device ->
                        device.defaultConfiguration.bounds.contains(mousePoint)
                    } ?: ge.defaultScreenDevice
                    val gc = screenDevice.defaultConfiguration
                    val bounds = gc.bounds
                    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)

                    val workAreaLeft = bounds.x + insets.left
                    val workAreaTop = bounds.y + insets.top
                    val workAreaRight = bounds.x + bounds.width - insets.right
                    val workAreaBottom = bounds.y + bounds.height - insets.bottom

                    val x = if (mouseX + prefSize.width > workAreaRight) {
                        maxOf(workAreaLeft, mouseX - prefSize.width)
                    } else {
                        mouseX
                    }

                    val y = if (mouseY + prefSize.height > workAreaBottom) {
                        maxOf(workAreaTop, mouseY - prefSize.height)
                    } else {
                        mouseY
                    }

                    menuDialog.location = Point(x, y)
                    menuDialog.isVisible = true
                    menuDialog.toFront()
                    menuDialog.requestFocus()
                }
            }
        }
        installedTrayIcon?.addMouseListener(mouseListener)
        onDispose {
            installedTrayIcon?.removeMouseListener(mouseListener)
            menuDialog.dispose()
        }
    }
}

private object ModernTrayMenu {
    fun isWindowsSystemDark(): Boolean {
        return try {
            val process = ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            ).start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            text.contains("0x0")
        } catch (_: Exception) {
            false
        }
    }

    fun createMenuPanel(
        appName: String,
        showText: String,
        quitText: String,
        isDark: Boolean,
        onShow: () -> Unit,
        onQuit: () -> Unit
    ): JPanel {
        val container = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val w = width
                val h = height

                // 柔和外阴影
                for (i in 0 until 3) {
                    g2.color = Color(0, 0, 0, (3 - i) * 6)
                    g2.drawRoundRect(i, i, w - 1 - i * 2, h - 1 - i * 2, 14, 14)
                }

                // 主体背景
                g2.color = if (isDark) Color(30, 32, 38, 252) else Color(255, 255, 255, 252)
                g2.fillRoundRect(3, 3, w - 6, h - 6, 12, 12)

                // 极细微边框
                g2.color = if (isDark) Color(60, 64, 76) else Color(226, 232, 240)
                g2.drawRoundRect(3, 3, w - 7, h - 7, 12, 12)
                g2.dispose()
            }
        }
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(7, 7, 7, 7)
        container.isOpaque = false

        // Header 标题
        val titleLabel = JLabel(appName).apply {
            font = Font("Microsoft YaHei UI", Font.BOLD, 11)
            foreground = if (isDark) Color(156, 163, 175) else Color(100, 116, 139)
            border = EmptyBorder(2, 6, 4, 6)
            icon = object : Icon {
                override fun getIconWidth(): Int = 12
                override fun getIconHeight(): Int = 12
                override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
                    if (g == null) return
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(34, 197, 94)
                    g2.fillOval(x + 1, y + 2, 7, 7)
                    g2.dispose()
                }
            }
            alignmentX = Component.LEFT_ALIGNMENT
        }
        container.add(titleLabel)

        // 分隔线
        val sep = object : JSeparator() {
            override fun paintComponent(g: Graphics) {
                g.color = if (isDark) Color(50, 54, 64) else Color(241, 245, 249)
                g.drawLine(4, height / 2, width - 4, height / 2)
            }
        }.apply {
            maximumSize = Dimension(Int.MAX_VALUE, 6)
            preferredSize = Dimension(170, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        container.add(sep)

        // 菜单项
        val itemShow = createMenuItem(showText, isDanger = false, isDark = isDark, onClick = onShow)
        itemShow.alignmentX = Component.LEFT_ALIGNMENT
        container.add(itemShow)

        val itemQuit = createMenuItem(quitText, isDanger = true, isDark = isDark, onClick = onQuit)
        itemQuit.alignmentX = Component.LEFT_ALIGNMENT
        container.add(itemQuit)

        return container
    }

    private fun createMenuItem(
        text: String,
        isDanger: Boolean,
        isDark: Boolean,
        onClick: () -> Unit
    ): JComponent {
        var isHovered = false
        val item = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val w = width
                val h = height

                if (isHovered) {
                    g2.color = if (isDanger) {
                        if (isDark) Color(69, 26, 30) else Color(254, 242, 242)
                    } else {
                        if (isDark) Color(48, 52, 62) else Color(241, 245, 249)
                    }
                    g2.fillRoundRect(2, 2, w - 4, h - 4, 8, 8)
                }

                val iconY = (h - 13) / 2
                if (isDanger) {
                    g2.color = if (isHovered) {
                        if (isDark) Color(248, 113, 113) else Color(225, 29, 72)
                    } else {
                        if (isDark) Color(156, 163, 175) else Color(148, 163, 184)
                    }
                    g2.stroke = BasicStroke(1.3f)
                    g2.drawArc(8, iconY, 12, 12, 130, 280)
                    g2.drawLine(14, iconY, 14, iconY + 5)
                } else {
                    g2.color = if (isHovered) {
                        if (isDark) Color(96, 165, 250) else Color(37, 99, 235)
                    } else {
                        if (isDark) Color(156, 163, 175) else Color(100, 116, 139)
                    }
                    g2.stroke = BasicStroke(1.3f)
                    g2.drawRoundRect(8, iconY, 12, 11, 2, 2)
                    g2.drawLine(8, iconY + 3, 20, iconY + 3)
                }

                g2.font = Font("Microsoft YaHei UI", Font.PLAIN, 12)
                val fm = g2.fontMetrics
                val textY = (h - fm.height) / 2 + fm.ascent
                g2.color = if (isHovered) {
                    if (isDanger) {
                        if (isDark) Color(248, 113, 113) else Color(225, 29, 72)
                    } else {
                        if (isDark) Color(243, 244, 246) else Color(15, 23, 42)
                    }
                } else {
                    if (isDark) Color(209, 213, 219) else Color(51, 65, 85)
                }
                g2.drawString(text, 28, textY)
                g2.dispose()
            }
        }

        item.isOpaque = false
        item.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        item.preferredSize = Dimension(176, 30)
        item.maximumSize = Dimension(Int.MAX_VALUE, 30)

        item.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                isHovered = true
                item.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                isHovered = false
                item.repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) {
                    onClick()
                }
            }
        })

        return item
    }
}
