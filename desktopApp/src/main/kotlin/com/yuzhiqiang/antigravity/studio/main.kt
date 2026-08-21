package com.yuzhiqiang.antigravity.studio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

fun main() = application {
    val windowState = rememberWindowState(
        width = 1080.dp,
        height = 720.dp,
        position = WindowPosition(Alignment.Center)
    )
    var isVisible by remember { mutableStateOf(true) }

    if (isVisible) {
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Antigravity Studio",
        ) {
            App()
        }
    }
}