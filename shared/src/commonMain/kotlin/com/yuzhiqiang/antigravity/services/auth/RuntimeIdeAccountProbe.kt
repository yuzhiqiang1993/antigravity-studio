package com.yuzhiqiang.antigravity.services.auth

import java.util.Locale

/**
 * 通过 Antigravity IDE 正在运行的 language_server 读取当前运行态账号。
 *
 * 该探针只接受能够从进程命令行证明属于 IDE 的 language_server（`--app_data_dir antigravity-ide`
 * 或安装路径包含 `Antigravity IDE`），不会把独立 App、CLI 或 Studio 的语言服务误当成 IDE。
 * 所有网络请求固定发往 127.0.0.1，只在显式探测期间读取进程参数，不读取钥匙串。
 *
 * IDE 可能同时运行多个 language_server 实例（每个工作区一个），所有实例共享同一个
 * 登录账号，因此只要有任意一个实例返回有效邮箱即视为探测成功。
 */
object RuntimeIdeAccountProbe {

    private val appCommandMarkers = listOf(
        "/antigravity.app/",
        "--standalone"
    )

    private val targetConfig = RuntimeAccountProbe.TargetConfig(
        displayName = "Antigravity IDE",
        processMatcher = { command, osName ->
            isAntigravityIdeCommand(command, osName)
        }
    )

    /**
     * 探测当前运行中的 Antigravity IDE 账号。
     */
    suspend fun detectProfile(): Result<HostAccountDetector.IdeAccountProfile?> =
        RuntimeAccountProbe.detectProfile(targetConfig)

    /** 只按可证明的 IDE 安装路径或 --app_data_dir antigravity-ide 识别。 */
    private fun isAntigravityIdeCommand(command: String, osName: String): Boolean {
        val normalized = command.lowercase(Locale.ROOT).replace('\\', '/')
        // 排除独立 App 和 Studio 的进程
        if (appCommandMarkers.any(normalized::contains)) return false

        val appDataDir = RuntimeAccountProbe.extractFlagValue(normalized, "--app_data_dir")
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }

        // 通过 --app_data_dir 精准识别
        if (appDataDir == "antigravity-ide") return true

        val subclientType = RuntimeAccountProbe.extractFlagValue(normalized, "--subclient_type")
            ?.trim()
        if (subclientType == "ide") return true

        // 插件安装路径特征（通用 VS Code 扩展）
        if (normalized.contains("extensions/antigravity/") ||
            normalized.contains("extensions\\antigravity\\")
        ) return true

        // 通过安装路径识别
        return when {
            osName.contains("mac") -> {
                normalized.contains("/antigravity ide.app/") ||
                        normalized.contains("/antigravity-ide.app/")
            }
            osName.contains("windows") -> {
                normalized.contains("/antigravity ide/") ||
                        normalized.contains("/antigravity-ide/") ||
                        normalized.contains("\\antigravity ide\\") ||
                        normalized.contains("\\antigravity-ide\\")
            }
            osName.contains("linux") -> {
                normalized.contains("/antigravity-ide/") ||
                        normalized.contains("/antigravity ide/")
            }
            else -> false
        }
    }
}
