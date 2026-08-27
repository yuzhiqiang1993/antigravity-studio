package com.yuzhiqiang.antigravity.ui.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * 将文本复制到系统剪贴板（跨平台安全执行）
 */
fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
