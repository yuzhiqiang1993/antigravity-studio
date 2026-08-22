package com.yuzhiqiang.antigravity.host.process

object ProcessDetector {

    fun isProcessRunning(processNameKeyword: String): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return try {
            if (os.contains("win")) {
                val process = ProcessBuilder("tasklist").start()
                val text = process.inputStream.bufferedReader().readText()
                text.contains(processNameKeyword, ignoreCase = true)
            } else {
                val process = ProcessBuilder("pgrep", "-f", processNameKeyword).start()
                process.waitFor() == 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
