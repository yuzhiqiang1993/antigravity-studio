package com.yuzhiqiang.antigravity.host.macos

object MacHostManager {

    /** 写入当前 macOS 登录会话的 CLOUD_CODE_URL。 */
    fun setEnvironmentUrl(endpoint: String): Boolean {
        return try {
            val process = ProcessBuilder("launchctl", "setenv", "CLOUD_CODE_URL", endpoint)
                .start()
            process.waitFor() == 0
        } catch (error: Exception) {
            false
        }
    }

    /** 删除当前 macOS 登录会话的 CLOUD_CODE_URL。 */
    fun unsetEnvironmentUrl(): Boolean {
        return try {
            val process = ProcessBuilder("launchctl", "unsetenv", "CLOUD_CODE_URL")
                .start()
            process.waitFor() == 0 || getEnvironmentUrl() == null
        } catch (error: Exception) {
            false
        }
    }

    /** 读取当前 macOS 登录会话的 CLOUD_CODE_URL。 */
    fun getEnvironmentUrl(): String? {
        return try {
            val process = ProcessBuilder("launchctl", "getenv", "CLOUD_CODE_URL").start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotEmpty()) {
                output
            } else {
                null
            }
        } catch (error: Exception) {
            null
        }
    }
}
