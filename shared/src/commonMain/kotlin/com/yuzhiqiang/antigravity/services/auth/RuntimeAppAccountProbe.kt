package com.yuzhiqiang.antigravity.services.auth

import java.util.Locale

/**
 * 通过 Antigravity App 与 CLI 正在运行的 language_server 读取当前运行态账号。
 *
 * 该探针只接受能够从进程命令行证明属于独立 App 或 CLI 的 language_server，
 * 不会把 IDE 或 Studio 自己的语言服务误当成 App/CLI。所有网络请求固定
 * 发往 127.0.0.1，并且只在显式探测期间读取进程参数，不读取钥匙串。
 */
object RuntimeAppAccountProbe {

    private val ideCommandMarkers = listOf(
        "antigravity ide",
        "antigravity-ide",
        "antigravity_ide",
        "antigravity-studio",
        "antigravity-ide-cockpit"
    )

    private val windowsAppCommandMarkers = listOf(
        "/antigravity/",
        "/programs/antigravity/",
        "\\antigravity\\",
        "\\programs\\antigravity\\",
        "antigravity-cli",
        "antigravity_cli"
    )

    private val linuxAppCommandMarkers = listOf(
        "/antigravity/",
        "/antigravity.app/",
        "antigravity-cli",
        "antigravity_cli"
    )

    private val targetConfig = RuntimeAccountProbe.TargetConfig(
        displayName = "Antigravity App & CLI",
        processMatcher = { command, osName ->
            isAntigravityAppCommand(command, osName)
        }
    )

    /**
     * 探测当前运行中的 Antigravity App 或 CLI 账号。
     */
    suspend fun detectProfile(): Result<HostAccountDetector.IdeAccountProfile?> =
        RuntimeAccountProbe.detectProfile(targetConfig)

    /** 只按可证明的 App/CLI 安装路径或 app_data_dir 识别，避免串到 IDE。 */
    private fun isAntigravityAppCommand(command: String, osName: String): Boolean {
        val normalized = command.lowercase(Locale.ROOT).replace('\\', '/')
        if (ideCommandMarkers.any(normalized::contains)) return false

        val appDataDir = RuntimeAccountProbe.extractFlagValue(normalized, "--app_data_dir")
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
        if (appDataDir == "antigravity-ide") {
            return false
        }
        if (appDataDir == "antigravity" || appDataDir == "antigravity-cli") {
            return true
        }

        return when {
            osName.contains("mac") -> {
                normalized.contains("/antigravity.app/") ||
                        normalized.contains("/antigravity app.app/") ||
                        normalized.contains("antigravity-cli") ||
                        normalized.contains("antigravity_cli") ||
                        appDataDir == "antigravity" ||
                        appDataDir == "antigravity-cli"
            }

            osName.contains("windows") -> {
                appDataDir == "antigravity" ||
                        appDataDir == "antigravity-cli" ||
                        windowsAppCommandMarkers.any(normalized::contains)
            }

            osName.contains("linux") -> {
                appDataDir == "antigravity" ||
                        appDataDir == "antigravity-cli" ||
                        linuxAppCommandMarkers.any(normalized::contains)
            }

            else -> false
        }
    }
}
