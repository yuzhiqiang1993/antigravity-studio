package com.yuzhiqiang.antigravity.host.windows

import java.util.concurrent.TimeUnit

object WindowsHostManager {

    @Volatile
    private var cachedEnvironmentUrl: String? = null
    @Volatile
    private var lastQueryTimeMs: Long = 0L
    private const val CACHE_TTL_MS = 200L

    /** 写入 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun setEnvironmentUrl(endpoint: String): Boolean {
        return try {
            // setx 会截断较长值且不会明确写入当前用户注册表；与 byok 一样直接更新 HKCU。
            val process = ProcessBuilder(
                "reg", "add", "HKCU\\Environment",
                "/v", "CLOUD_CODE_URL",
                "/t", "REG_SZ",
                "/d", endpoint,
                "/f"
            ).start()
            val success = process.waitFor() == 0
            if (success) {
                cachedEnvironmentUrl = endpoint
                lastQueryTimeMs = System.currentTimeMillis()
                // 已启动的宿主不会自动读取新环境；异步广播变更供新进程和宿主发现逻辑刷新，不阻塞主流程。
                broadcastEnvironmentChangeAsync()
            }
            success
        } catch (error: Exception) {
            false
        }
    }

    /** 删除 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun unsetEnvironmentUrl(): Boolean {
        return try {
            val process = ProcessBuilder("reg", "delete", "HKCU\\Environment", "/F", "/V", "CLOUD_CODE_URL").start()
            val success = process.waitFor() == 0 || getEnvironmentUrl() == null
            if (success) {
                cachedEnvironmentUrl = null
                lastQueryTimeMs = System.currentTimeMillis()
                broadcastEnvironmentChangeAsync()
            }
            success
        } catch (error: Exception) {
            false
        }
    }

    /** 读取 Windows 当前用户的 CLOUD_CODE_URL。 */
    fun getEnvironmentUrl(): String? {
        val now = System.currentTimeMillis()
        if (now - lastQueryTimeMs < CACHE_TTL_MS) {
            return cachedEnvironmentUrl
        }
        return try {
            val process = ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", "CLOUD_CODE_URL").start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            val result = if (exitCode != 0) {
                null
            } else {
                output.lineSequence()
                    .firstOrNull { line -> line.contains("CLOUD_CODE_URL", ignoreCase = true) }
                    ?.let { line ->
                        val parts = line.trim().split(Regex("\\s+"), limit = 3)
                        if (parts.size >= 3) {
                            parts[2].trim()
                        } else {
                            line.substringAfter("REG_EXPAND_SZ", "")
                                .ifBlank { line.substringAfter("REG_SZ", "") }
                                .trim()
                        }
                    }
                    ?.takeIf { value -> value.isNotEmpty() }
            }
            cachedEnvironmentUrl = result
            lastQueryTimeMs = now
            result
        } catch (error: Exception) {
            null
        }
    }

    private fun broadcastEnvironmentChangeAsync() {
        Thread {
            runCatching {
                ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                    "\$signature = '[DllImport(\"user32.dll\", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint flags, uint timeout, out UIntPtr result);'; Add-Type -MemberDefinition \$signature -Name NativeMethods -Namespace Win32; \$result = [UIntPtr]::Zero; [Win32.NativeMethods]::SendMessageTimeout([IntPtr]0xffff, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]\$result) | Out-Null"
                ).start().waitFor(5, TimeUnit.SECONDS)
            }
        }.apply {
            isDaemon = true
            name = "windows-env-broadcast"
            start()
        }
    }
}
