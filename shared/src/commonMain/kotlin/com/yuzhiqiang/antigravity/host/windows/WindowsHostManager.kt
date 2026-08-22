package com.yuzhiqiang.antigravity.host.windows

object WindowsHostManager {

    /** 写入 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun setEnvironmentUrl(endpoint: String): Boolean {
        return try {
            val process = ProcessBuilder("setx", "CLOUD_CODE_URL", endpoint).start()
            process.waitFor() == 0
        } catch (error: Exception) {
            false
        }
    }

    /** 删除 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun unsetEnvironmentUrl(): Boolean {
        return try {
            val process = ProcessBuilder("reg", "delete", "HKCU\\Environment", "/F", "/V", "CLOUD_CODE_URL").start()
            process.waitFor() == 0 || getEnvironmentUrl() == null
        } catch (error: Exception) {
            false
        }
    }

    /** 读取 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun getEnvironmentUrl(): String? {
        return try {
            val process = ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", "CLOUD_CODE_URL").start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                null
            } else {
                output.lineSequence()
                    .firstOrNull { line -> line.contains("CLOUD_CODE_URL") }
                    ?.substringAfter("REG_SZ", "")
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }
            }
        } catch (error: Exception) {
            null
        }
    }
}
