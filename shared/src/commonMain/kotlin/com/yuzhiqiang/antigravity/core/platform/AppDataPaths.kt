package com.yuzhiqiang.antigravity.core.platform

import java.io.File

/**
 * Studio 自有持久化数据的统一路径入口。
 *
 * 外部宿主的凭据和配置路径不由此处解析；它们仍由对应的宿主服务负责。
 */
object AppDataPaths {
    const val CONFIG_FILE_NAME = "config.v2.json"
    const val ACCOUNTS_FILE_NAME = "accounts.v1.json"
    const val QUOTAS_FILE_NAME = "quotas.v1.json"
    const val ENVIRONMENT_RECEIPT_FILE_NAME = "environment-ownership.json"
    const val IDE_RECEIPT_FILE_NAME = "ide-settings-ownership.json"

    private const val CONFIG_PATH_ENV = "ANTIGRAVITY_STUDIO_CONFIG_PATH"
    private const val STUDIO_HOME_ENV = "ANTIGRAVITY_STUDIO_HOME"

    /** 返回当前 Studio 自有数据根目录。 */
    fun rootDir(): File {
        configuredConfigPath()?.parentFile?.let { return it }
        configuredStudioHome()?.let { return it }

        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name", "").lowercase()
        return when {
            osName.contains("mac") -> File(userHome, "Library/Application Support/Antigravity Studio")
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, "AppData/Roaming").absolutePath
                File(appData, "Antigravity Studio")
            }

            else -> {
                val configHome = System.getenv("XDG_CONFIG_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?: File(userHome, ".config").absolutePath
                File(configHome, "Antigravity Studio")
            }
        }
    }

    /** 返回 Studio 配置文件；测试或嵌入场景可传入独立根目录。 */
    fun configFile(customRootDir: File? = null): File {
        customRootDir?.let { return File(it, CONFIG_FILE_NAME) }
        return configuredConfigPath() ?: File(rootDir(), CONFIG_FILE_NAME)
    }

    /** 返回根目录下的 Studio 自有文件。 */
    fun resolve(fileName: String): File = File(rootDir(), fileName)

    /** 当前配置路径覆盖项非法时返回可展示的错误信息。 */
    fun configPathError(): String? {
        val configured = System.getenv(CONFIG_PATH_ENV)?.trim().orEmpty()
        return if (configured.isNotEmpty() && !File(configured).isAbsolute) {
            "$CONFIG_PATH_ENV 必须是绝对路径"
        } else {
            null
        }
    }

    private fun configuredConfigPath(): File? {
        return System.getenv(CONFIG_PATH_ENV)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf(File::isAbsolute)
    }

    private fun configuredStudioHome(): File? {
        return System.getenv(STUDIO_HOME_ENV)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf(File::isAbsolute)
    }
}
