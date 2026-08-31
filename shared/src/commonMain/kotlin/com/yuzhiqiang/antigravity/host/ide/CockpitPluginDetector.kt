package com.yuzhiqiang.antigravity.host.ide

import java.io.File

/**
 * 探测 Antigravity Cockpit (VS Code / IDE 插件) 是否已安装在用户系统中。
 */
object CockpitPluginDetector {

    fun isInstalled(): Boolean {
        try {
            val userHome = System.getProperty("user.home") ?: return false
            val candidateDirs = buildList {
                add(File(userHome, ".antigravity-ide/extensions"))
                add(File(userHome, ".antigravity/extensions"))
                add(File(userHome, ".vscode/extensions"))
                add(File(userHome, ".vscode-insiders/extensions"))
                add(File(userHome, ".cursor/extensions"))
                add(File(userHome, ".windsurf/extensions"))

                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) {
                    add(File(appData, "Antigravity/extensions"))
                    add(File(appData, "Code/extensions"))
                }
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrBlank()) {
                    add(File(localAppData, "Programs/Antigravity IDE/resources/app/extensions"))
                }
            }

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val found = dir.listFiles { f ->
                        f.isDirectory && f.name.contains("antigravity-ide-cockpit", ignoreCase = true)
                    }
                    if (!found.isNullOrEmpty()) {
                        return true
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return false
    }
}
