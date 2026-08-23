package com.yuzhiqiang.antigravity.host.windows

object WindowsHostManager {

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
                // 已启动的宿主不会自动读取新环境；广播变更供新进程和宿主发现逻辑及时刷新。
                runCatching {
                    ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                        "\$signature = '[DllImport(\"user32.dll\", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint flags, uint timeout, out UIntPtr result);'; Add-Type -MemberDefinition \$signature -Name NativeMethods -Namespace Win32; \$result = [UIntPtr]::Zero; [Win32.NativeMethods]::SendMessageTimeout([IntPtr]0xffff, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]\$result) | Out-Null"
                    ).start()
                }
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
                runCatching {
                    ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                        "\$signature = '[DllImport(\"user32.dll\", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint flags, uint timeout, out UIntPtr result);'; Add-Type -MemberDefinition \$signature -Name NativeMethods -Namespace Win32; \$result = [UIntPtr]::Zero; [Win32.NativeMethods]::SendMessageTimeout([IntPtr]0xffff, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]\$result) | Out-Null"
                    ).start()
                }
            }
            success
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
        } catch (error: Exception) {
            null
        }
    }
}
