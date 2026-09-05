package com.yuzhiqiang.antigravity.core.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/**
 * JVM 桌面平台底层实现，提供安全的浏览器调用、目录浏览与文件定位服务。
 */
internal actual object DesktopPlatformService {

    private val osName = System.getProperty("os.name", "").lowercase()
    internal actual val isMac: Boolean = osName.contains("mac") || osName.contains("darwin")
    internal actual val isWindows: Boolean = !isMac && osName.contains("win")

    internal actual fun openBrowser(url: String): Boolean {
        if (url.isBlank()) return false
        if (tryDesktop(Desktop.Action.BROWSE) { desktop -> desktop.browse(URI(url)) }) {
            return true
        }
        return runCatching {
            when {
                isMac -> ProcessBuilder("open", url).start()
                isWindows -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                else -> ProcessBuilder("xdg-open", url).start()
            }
            true
        }.getOrDefault(false)
    }

    internal actual fun openFile(file: File): Boolean {
        if (tryDesktop(Desktop.Action.OPEN) { desktop -> desktop.open(file) }) {
            return true
        }
        return runCatching {
            when {
                isMac -> ProcessBuilder("open", file.absolutePath).start()
                isWindows -> ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                else -> ProcessBuilder("xdg-open", file.absolutePath).start()
            }
            true
        }.getOrDefault(false)
    }

    internal actual fun launchInstaller(file: File): Boolean {
        return runCatching {
            when {
                isMac -> {
                    val process = ProcessBuilder("open", file.absolutePath).start()
                    process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                }

                isWindows -> {
                    ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                }

                !tryDesktop(Desktop.Action.OPEN) { desktop -> desktop.open(file) } -> {
                    ProcessBuilder("xdg-open", file.absolutePath).start()
                }
            }
            true
        }.getOrDefault(false)
    }

    internal actual fun openDirectory(directory: File): Boolean {
        return openFile(directory)
    }

    internal actual fun revealInFileManager(file: File): Boolean {
        return runCatching {
            when {
                isMac -> {
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                    true
                }

                isWindows -> {
                    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                    true
                }

                else -> {
                    val parent = file.parentFile ?: file
                    openDirectory(parent)
                }
            }
        }.getOrDefault(false)
    }

    private fun tryDesktop(action: Desktop.Action, operation: (Desktop) -> Unit): Boolean {
        return runCatching {
            if (!Desktop.isDesktopSupported()) return@runCatching false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(action)) return@runCatching false
            operation(desktop)
            true
        }.getOrDefault(false)
    }

    internal actual fun pickFile(title: String, filterExtension: String?): File? {
        return runCatching {
            val fileDialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            if (!filterExtension.isNullOrBlank()) {
                fileDialog.setFilenameFilter { _, name -> name.endsWith(".$filterExtension", ignoreCase = true) }
            }
            fileDialog.isVisible = true
            val file = fileDialog.file
            val dir = fileDialog.directory
            if (file != null && dir != null) File(dir, file) else null
        }.getOrNull()
    }

    internal actual fun pickSaveFile(title: String, defaultName: String?, filterExtension: String?): File? {
        return runCatching {
            val fileDialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
            if (!defaultName.isNullOrBlank()) {
                fileDialog.file = defaultName
            }
            if (!filterExtension.isNullOrBlank()) {
                fileDialog.setFilenameFilter { _, name -> name.endsWith(".$filterExtension", ignoreCase = true) }
            }
            fileDialog.isVisible = true
            val file = fileDialog.file
            val dir = fileDialog.directory
            if (file != null && dir != null) File(dir, file) else null
        }.getOrNull()
    }

    internal actual fun pickPath(title: String, forDirectory: Boolean): File? {
        return runCatching {
            if (isMac) {
                if (forDirectory) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "true")
                }
                val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (forDirectory) {
                    System.setProperty("apple.awt.fileDialogForDirectories", "false")
                }
                when {
                    dir != null && file != null -> File(dir, file)
                    dir != null -> File(dir)
                    else -> null
                }
            } else {
                val chooser = JFileChooser()
                chooser.dialogTitle = title
                chooser.fileSelectionMode = if (forDirectory) {
                    JFileChooser.DIRECTORIES_ONLY
                } else {
                    JFileChooser.FILES_AND_DIRECTORIES
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            }
        }.getOrNull()
    }
}
