package com.yuzhiqiang.antigravity.ui.screens.models

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun copyTextToClipboard(value: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}

