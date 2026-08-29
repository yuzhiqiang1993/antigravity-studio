package com.yuzhiqiang.antigravity.ui.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * 将文本复制到系统剪贴板。
 *
 * @return 是否成功写入，调用方应仅在成功时展示“已复制”反馈
 */
fun copyToClipboard(text: String): Boolean {
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)
}

/**
 * 从系统剪贴板读取纯文本内容
 */
fun readFromClipboard(): String? {
    return runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()
}

/** 兼容旧函数名，并保持原有 Unit 返回类型。 */
fun copyTextToClipboard(text: String) {
    copyToClipboard(text)
}
