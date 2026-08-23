package com.yuzhiqiang.antigravity.host.process

object ProcessDetector {

    fun isProcessRunning(processNameKeyword: String): Boolean {
        return HostProcessManager.isProcessRunning(listOf(processNameKeyword))
    }
}
