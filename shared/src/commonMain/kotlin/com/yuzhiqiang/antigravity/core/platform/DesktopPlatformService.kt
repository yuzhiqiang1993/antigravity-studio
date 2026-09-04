package com.yuzhiqiang.antigravity.core.platform

import java.io.File

/**
 * 桌面平台底层抽象契约，由各平台源集 (jvmMain 等) 提供平台原生实现。
 */
internal expect object DesktopPlatformService {
    internal val isMac: Boolean
    internal val isWindows: Boolean

    internal fun openBrowser(url: String): Boolean
    internal fun openFile(file: File): Boolean
    internal fun launchInstaller(file: File): Boolean
    internal fun openDirectory(directory: File): Boolean
    internal fun revealInFileManager(file: File): Boolean
}
