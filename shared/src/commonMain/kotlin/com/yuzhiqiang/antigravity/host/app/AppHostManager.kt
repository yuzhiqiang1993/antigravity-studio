package com.yuzhiqiang.antigravity.host.app

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.process.HostProcessManager
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.delay
import java.io.File

/**
 * App 宿主跨平台集成管理器（支持 macOS 与 Windows 双平台）。
 */
object AppHostManager {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    /**
     * 检测 Antigravity App 是否已安装。
     */
    fun isInstalled(customInstallation: String? = null): Boolean {
        if (!customInstallation.isNullOrBlank()) {
            val file = File(customInstallation.trim())
            if (file.exists()) {
                if (file.isFile) return true
                if (file.isDirectory && (File(file, "Contents/MacOS/Antigravity").isFile || File(
                        file,
                        "Antigravity.exe"
                    ).isFile)
                ) return true
            }
        }
        val candidates = getCandidateInstallations(customInstallation)
        return if (isWindows) {
            candidates.any { root ->
                root.isDirectory &&
                        File(root, "Antigravity.exe").isFile &&
                        (File(root, "resources/bin/language_server.exe").isFile ||
                                File(root, "resources/bin/language_server.original.exe").isFile)
            }
        } else {
            candidates.any { root ->
                root.isDirectory &&
                        File(root, "Contents/MacOS/Antigravity").isFile &&
                        (File(root, "Contents/Resources/bin/language_server").isFile ||
                                File(root, "Contents/Resources/bin/language_server.original").isFile)
            }
        }
    }

    private val appMatchPatterns = if (isWindows) {
        listOf("Antigravity.exe")
    } else {
        listOf("Antigravity.app/", "/Antigravity.app", "/MacOS/Antigravity")
    }

    private val appExcludePatterns = listOf(
        "Antigravity IDE",
        "Antigravity-IDE",
        "Antigravity IDE.exe",
        "Antigravity Studio",
        "antigravity-studio",
        "Antigravity Studio.app",
        "antigravity-ide-cockpit"
    )

    private val appLanguageServerPatterns = if (isWindows) {
        listOf(
            "Programs\\Antigravity\\resources\\bin\\language_server.exe",
            "Programs/Antigravity/resources/bin/language_server.exe",
            "Programs\\Antigravity\\resources\\bin\\language_server.original.exe",
            "Programs/Antigravity/resources/bin/language_server.original.exe"
        )
    } else {
        listOf(
            "Antigravity.app/Contents/Resources/bin/language_server",
            "Antigravity.app/Contents/Resources/bin/language_server.original"
        )
    }

    /**
     * 获取候选安装根目录列表。
     */
    fun getCandidateInstallations(customInstallation: String? = null): List<File> {
        val customRoot = customInstallation?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::normalizeCustomInstallation)
        if (customRoot != null) {
            return listOf(customRoot)
        }
        val userHome = System.getProperty("user.home", "")
        val os = System.getProperty("os.name", "").lowercase()
        val candidates = if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            buildList {
                add(File(localAppData, "Programs/Antigravity"))
                add(File(programFiles, "Antigravity"))
                System.getenv("ProgramFiles(x86)")?.let { add(File(it, "Antigravity")) }
                addAll(discoverWindowsInstallations("Antigravity.exe"))
            }
        } else if (os.contains("mac")) {
            buildList {
                add(File("/Applications/Antigravity.app"))
                add(File("$userHome/Applications/Antigravity.app"))
                add(File("/Applications/Antigravity App.app"))
                add(File("$userHome/Applications/Antigravity App.app"))
                addAll(discoverMacApplications("com.google.antigravity"))
            }
        } else {
            buildList {
                add(File("/opt/antigravity"))
                add(File("/usr/share/antigravity"))
                add(File(userHome, ".local/share/antigravity"))
            }
        }
        return candidates.filterNot { file ->
            val path = file.absolutePath.replace('\\', '/')
            path.contains("/Antigravity IDE", ignoreCase = true) ||
                    path.contains("/Antigravity-IDE", ignoreCase = true) ||
                    path.contains("/Antigravity Studio", ignoreCase = true)
        }.distinct()
    }

    /**
     * 获取当前 language_server 可执行文件。
     */
    fun getLanguageServerFile(customInstallation: String? = null): File? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val lsFile = if (isWindows) {
                File(root, "resources/bin/language_server.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server")
            }
            val origFile = if (isWindows) {
                File(root, "resources/bin/language_server.original.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server.original")
            }
            if (lsFile.exists() || origFile.exists()) {
                return lsFile
            }
        }
        return null
    }

    /**
     * 获取被备份的原始 language_server 二进制文件。
     */
    fun getOriginalLanguageServerFile(customInstallation: String? = null): File? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val origFile = if (isWindows) {
                File(root, "resources/bin/language_server.original.exe")
            } else {
                File(root, "Contents/Resources/bin/language_server.original")
            }
            if (origFile.exists()) {
                return origFile
            }
        }
        return null
    }

    /**
     * 检测指定文件是否包含 Shim 脚本标识。
     */
    private fun isShimScript(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(1024)
                val read = input.read(buffer)
                if (read > 0) {
                    val text = String(buffer, 0, read, Charsets.UTF_8)
                    text.contains("ANTIGRAVITY_STUDIO_MANAGED_SHIM")
                } else {
                    false
                }
            }
        }.getOrDefault(false)
    }

    private data class LanguageServerFiles(
        val languageServer: File,
        val original: File,
        val cmdShim: File
    )

    private fun languageServerFiles(root: File): LanguageServerFiles {
        val binDir = if (isWindows) File(root, "resources/bin") else File(root, "Contents/Resources/bin")
        return LanguageServerFiles(
            languageServer = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server"),
            original = if (isWindows) File(binDir, "language_server.original.exe") else File(
                binDir,
                "language_server.original"
            ),
            cmdShim = File(binDir, "language_server.cmd")
        )
    }

    /**
     * 检测是否存在可执行的 Language Server Shim。
     *
     * 仅有 `.original` 备份代表上一次接入未完成，不能作为当前可用代理接入状态。
     */
    private fun isShimReady(customInstallation: String? = null): Boolean {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val files = languageServerFiles(root)
            if (!files.original.isFile) continue
            if (isWindows) {
                if (isShimScript(files.cmdShim)) return true
            } else if (isShimScript(files.languageServer) && files.languageServer.canExecute()) {
                return true
            }
        }
        return false
    }

    private fun hasOriginalLanguageServer(customInstallation: String? = null): Boolean {
        return getCandidateInstallations(customInstallation).any { root ->
            root.exists() && languageServerFiles(root).original.isFile
        }
    }

    private fun readCurrentShimContent(customInstallation: String?): String? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val files = languageServerFiles(root)
            val shimFile = if (isWindows) files.cmdShim else files.languageServer
            if (isShimScript(shimFile)) {
                return runCatching { shimFile.readText(Charsets.UTF_8) }.getOrNull()
            }
        }
        return null
    }

    private fun restoreShimContent(content: String, customInstallation: String?): Boolean {
        val candidates = getCandidateInstallations(customInstallation)
        var restored = false
        for (root in candidates) {
            if (!root.exists()) continue
            val files = languageServerFiles(root)
            val shimFile = if (isWindows) files.cmdShim else files.languageServer
            val writeResult = AtomicFileWriter.writeText(
                target = shimFile,
                content = content,
                permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                disallowSymlinks = true
            )
            if (writeResult.isFailure) continue
            if (!isWindows) shimFile.setExecutable(true, false)
            restored = true
        }
        return restored
    }

    /**
     * 检测 Shim 或其未完成接入残留，供还原/修复入口使用。
     */
    fun isShimInstalled(customInstallation: String? = null): Boolean {
        return isShimReady(customInstallation) || hasOriginalLanguageServer(customInstallation)
    }

    private fun hasShimResidue(customInstallation: String? = null): Boolean {
        return isShimInstalled(customInstallation)
    }

    private fun copyFile(source: File, target: File): Boolean {
        if (!source.exists() || !source.isFile) return false
        return try {
            java.nio.file.Files.copy(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
            )
            target.isFile && target.length() == source.length()
        } catch (error: Exception) {
            AppLog.e("Host/App", error) { "复制 language_server 失败：${source.absolutePath} -> ${target.absolutePath}" }
            false
        }
    }

    private fun moveOrReplaceFile(source: File, target: File): Boolean {
        if (!source.exists()) return false
        return try {
            try {
                java.nio.file.Files.move(
                    source.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                true
            } catch (_: Exception) {
                java.nio.file.Files.move(
                    source.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                true
            }
        } catch (_: Exception) {
            try {
                java.nio.file.Files.copy(
                    source.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                source.delete()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun buildWindowsShimScript(targetEndpoint: String): String {
        return buildString {
            appendLine("@echo off")
            appendLine("rem ANTIGRAVITY_STUDIO_MANAGED_SHIM")
            appendLine("setlocal enabledelayedexpansion")
            appendLine("set \"DIR=%~dp0\"")
            appendLine("set \"ORIGINAL=%DIR%language_server.original.exe\"")
            appendLine("set \"TARGET_URL=$targetEndpoint\"")
            appendLine("if defined CLOUD_CODE_URL set \"TARGET_URL=%CLOUD_CODE_URL%\"")
            appendLine("set \"NEW_ARGS=\"")
            appendLine("set \"SKIP_NEXT=0\"")
            appendLine("for %%A in (%*) do (")
            appendLine("    if !SKIP_NEXT! equ 1 (")
            appendLine("        set \"NEW_ARGS=!NEW_ARGS! !TARGET_URL!\"")
            appendLine("        set \"SKIP_NEXT=0\"")
            appendLine("    ) else if \"%%~A\"==\"--cloud_code_endpoint\" (")
            appendLine("        set \"NEW_ARGS=!NEW_ARGS! %%~A\"")
            appendLine("        set \"SKIP_NEXT=1\"")
            appendLine("    ) else (")
            appendLine("        set \"ARG=%%~A\"")
            appendLine("        if \"!ARG:~0,22!\"==\"--cloud_code_endpoint=\" (")
            appendLine("            set \"NEW_ARGS=!NEW_ARGS! --cloud_code_endpoint=!TARGET_URL!\"")
            appendLine("        ) else (")
            appendLine("            set \"NEW_ARGS=!NEW_ARGS! %%~A\"")
            appendLine("        )")
            appendLine("    )")
            appendLine(")")
            appendLine("\"%ORIGINAL%\" %NEW_ARGS%")
        }
    }

    private fun buildMacShimScript(targetEndpoint: String): String {
        return """
            #!/bin/bash
            # ANTIGRAVITY_STUDIO_MANAGED_SHIM
            DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            ORIGINAL_BIN="${'$'}DIR/language_server.original"

            TARGET_URL="${'$'}{CLOUD_CODE_URL}"
            if [ -z "${'$'}TARGET_URL" ]; then
                TARGET_URL="$(launchctl getenv CLOUD_CODE_URL 2>/dev/null)"
            fi
            if [ -z "${'$'}TARGET_URL" ]; then
                TARGET_URL="$targetEndpoint"
            fi

            ARGS=()
            SKIP_NEXT=0
            HAS_ENDPOINT=0

            for arg in "${'$'}@"; do
                if [ "${'$'}SKIP_NEXT" -eq 1 ]; then
                    ARGS+=("${'$'}TARGET_URL")
                    SKIP_NEXT=0
                    HAS_ENDPOINT=1
                    continue
                fi
                if [ "${'$'}arg" = "--cloud_code_endpoint" ]; then
                    ARGS+=("${'$'}arg")
                    SKIP_NEXT=1
                    continue
                fi
                if [[ "${'$'}arg" =~ ^--cloud_code_endpoint= ]]; then
                    ARGS+=("--cloud_code_endpoint=${'$'}TARGET_URL")
                    HAS_ENDPOINT=1
                    continue
                fi
                ARGS+=("${'$'}arg")
            done

            if [ "${'$'}HAS_ENDPOINT" -eq 0 ] && [ "${'$'}SKIP_NEXT" -eq 0 ]; then
                ARGS+=("--cloud_code_endpoint" "${'$'}TARGET_URL")
            fi

            exec "${'$'}ORIGINAL_BIN" "${'$'}{ARGS[@]}"
        """.trimIndent()
    }

    /**
     * 安装 Language Server Shim 包装脚本，将写死的 --cloud_code_endpoint 动态重写为本地代理端口。
     */
    fun installLanguageServerShim(proxyPort: Int, customInstallation: String? = null): Boolean {
        val candidates = getCandidateInstallations(customInstallation)
        AppLog.w("Host/App") {
            "安装 Shim：port=$proxyPort custom=${customInstallation ?: "<auto>"} candidates=${candidates.map { it.absolutePath }}"
        }
        var anySuccess = false
        for (root in candidates) {
            if (!root.exists()) {
                AppLog.w("Host/App") { "跳过不存在的候选目录：${root.absolutePath}" }
                continue
            }
            val files = languageServerFiles(root)
            val binDir = files.languageServer.parentFile ?: continue
            if (!binDir.exists() && !binDir.mkdirs()) {
                AppLog.e("Host/App") { "无法创建 bin 目录：${binDir.absolutePath}" }
                continue
            }

            val lsFile = files.languageServer
            val origFile = files.original
            val cmdFile = files.cmdShim
            val hadOriginal = origFile.exists()
            val hadShim = if (isWindows) isShimScript(cmdFile) else isShimScript(lsFile)
            AppLog.w("Host/App") {
                "候选 ${root.absolutePath} ls=${lsFile.exists()}/${lsFile.length()} orig=${origFile.exists()}/${origFile.length()} shim=$hadShim"
            }
            val previousShimContent = if (hadShim) {
                val previousShimFile = if (isWindows) cmdFile else lsFile
                runCatching { previousShimFile.readText(Charsets.UTF_8) }.getOrNull()
            } else {
                null
            }

            // 1. 备份原生二进制。macOS 优先复制，避免 rename 正在/刚退出的可执行文件触发 ETXTBSY。
            if (!origFile.exists()) {
                if (!lsFile.exists() || isShimScript(lsFile)) {
                    AppLog.e("Host/App") {
                        "无法备份 language_server：exists=${lsFile.exists()} isShim=${isShimScript(lsFile)} path=${lsFile.absolutePath}"
                    }
                    continue
                }
                val backedUp = if (isWindows) {
                    moveOrReplaceFile(lsFile, origFile)
                } else {
                    copyFile(lsFile, origFile)
                }
                if (!backedUp) {
                    AppLog.e("Host/App") { "备份 language_server 失败：${lsFile.absolutePath}" }
                    continue
                }
            }
            if (!origFile.exists()) {
                AppLog.e("Host/App") { "备份后仍缺少 original：${origFile.absolutePath}" }
                continue
            }

            // 2. 使用原子写入更新包装脚本，避免 App 进程恰好读取到半截脚本
            val targetEndpoint = "http://127.0.0.1:$proxyPort"
            val ok = if (isWindows) {
                val scriptContent = buildWindowsShimScript(targetEndpoint)
                val writeResult = AtomicFileWriter.writeText(
                    target = cmdFile,
                    content = scriptContent,
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeResult.isFailure) {
                    AppLog.e("Host/App", writeResult.exceptionOrNull()) {
                        "写入 Windows Shim 失败：${cmdFile.absolutePath}"
                    }
                }
                writeResult.isSuccess &&
                        (!lsFile.exists() || !isShimScript(lsFile) || lsFile.delete()) &&
                        isShimScript(cmdFile)
            } else {
                val scriptContent = buildMacShimScript(targetEndpoint)
                val writeResult = AtomicFileWriter.writeText(
                    target = lsFile,
                    content = scriptContent + "\n",
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeResult.isFailure) {
                    AppLog.e("Host/App", writeResult.exceptionOrNull()) {
                        "写入 macOS Shim 失败：${lsFile.absolutePath}"
                    }
                }
                val executable = lsFile.setExecutable(true, false) || lsFile.canExecute()
                writeResult.isSuccess && executable && isShimScript(lsFile)
            }
            if (ok) {
                anySuccess = true
                continue
            }

            // 写入失败时撤销本轮备份，避免只剩 original 却被误认为已经接入。
            if (!hadOriginal && origFile.exists()) {
                if (isWindows) {
                    if (cmdFile.exists() && isShimScript(cmdFile)) cmdFile.delete()
                } else if (lsFile.exists() && isShimScript(lsFile)) {
                    lsFile.delete()
                }
                when {
                    !lsFile.exists() -> moveOrReplaceFile(origFile, lsFile)
                    !isShimScript(lsFile) -> origFile.delete()
                }
            } else if (hadShim && previousShimContent != null) {
                val previousShimFile = if (isWindows) cmdFile else lsFile
                AtomicFileWriter.writeText(
                    target = previousShimFile,
                    content = previousShimContent,
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (!isWindows) previousShimFile.setExecutable(true, false)
            }
        }
        return anySuccess
    }

    /**
     * 还原原始 language_server 二进制文件（彻底清除包装器脚本与残留备份）。
     */
    fun restoreOriginalLanguageServer(customInstallation: String? = null): Boolean {
        val candidates = getCandidateInstallations(customInstallation)
        var anyRestoredOrClean = false
        for (root in candidates) {
            if (!root.exists()) continue
            val binDir = if (isWindows) File(root, "resources/bin") else File(root, "Contents/Resources/bin")
            if (!binDir.exists()) continue

            // 1. Windows 下清理 language_server.cmd
            if (isWindows) {
                val cmdFile = File(binDir, "language_server.cmd")
                if (cmdFile.exists()) {
                    cmdFile.delete()
                }
            }

            val lsFile = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server")
            val origFile = if (isWindows) File(binDir, "language_server.original.exe") else File(
                binDir,
                "language_server.original"
            )

            // 2. 如果 lsFile 存在且是 shim 脚本，则删除 shim 脚本
            if (lsFile.exists() && isShimScript(lsFile)) {
                lsFile.delete()
            }

            // 3. 还原 origFile
            if (origFile.exists()) {
                if (!lsFile.exists()) {
                    val moved = moveOrReplaceFile(origFile, lsFile)
                    if (moved) {
                        if (!isWindows) lsFile.setExecutable(true, false)
                        anyRestoredOrClean = true
                    }
                } else if (!isShimScript(lsFile)) {
                    // lsFile 存在且是正常二进制，origFile 只是多余备份，直接清理
                    origFile.delete()
                    anyRestoredOrClean = true
                }
            } else if (lsFile.exists() && !isShimScript(lsFile)) {
                anyRestoredOrClean = true
            }
        }
        return anyRestoredOrClean || candidates.none { it.exists() }
    }

    /**
     * 检测 Antigravity App 是否正在运行（精确匹配 App 进程并排除 IDE 进程）。
     */
    fun isRunning(customInstallation: String? = null): Boolean {
        return HostProcessManager.isProcessRunning(
            buildProcessPatterns(customInstallation),
            appExcludePatterns
        )
    }

    fun findPids(customInstallation: String? = null): List<Long> {
        return HostProcessManager.findProcessPids(
            buildProcessPatterns(customInstallation),
            appExcludePatterns
        )
    }

    /**
     * 检测是否已设置代理（包含环境变量与 Shim 包装器）。
     */
    fun isActive(proxyPort: Int, customInstallation: String? = null): Boolean {
        val environment = HostOwnershipStore.inspectEnvironmentIntegration(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        )
        return environment.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED &&
                environment.endpointMatches &&
                isShimReady(customInstallation)
    }

    fun detectVersion(customInstallation: String? = null): String? {
        val candidates = getCandidateInstallations(customInstallation)
        for (root in candidates) {
            if (!root.exists()) continue
            val infoPlist = File(root, "Contents/Info.plist")
            if (infoPlist.exists()) {
                val content = runCatching { infoPlist.readText() }.getOrNull() ?: continue
                val match = Regex("<key>CFBundleShortVersionString</key>\\s*<string>([^<]+)</string>").find(content)
                    ?: Regex("<key>CFBundleVersion</key>\\s*<string>([^<]+)</string>").find(content)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            val packageJson = File(root, "resources/app/package.json")
            if (packageJson.exists()) {
                val content = runCatching { packageJson.readText() }.getOrNull() ?: continue
                val match = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(content)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
        }
        return null
    }

    fun inspect(
        proxyPort: Int,
        isProxyRunning: Boolean = false,
        customInstallation: String? = null
    ): com.yuzhiqiang.antigravity.host.model.HostDetailedStatus {
        val installed = isInstalled(customInstallation)
        val running = installed && isRunning(customInstallation)
        val version = if (installed) runCatching { detectVersion(customInstallation) }.getOrNull() else null
        val inspect = HostOwnershipStore.inspectEnvironmentIntegration(
            HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort
        )
        val shimReady = isShimReady(customInstallation)
        val shimResidue = hasShimResidue(customInstallation)
        val finalState = when {
            shimReady && inspect.endpointMatches -> inspect.state
            shimReady || inspect.endpointMatches || shimResidue ->
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH

            else -> inspect.state
        }

        val configState = when (finalState) {
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_ENABLED
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.CONFLICT,
            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE

            com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NEEDS_UPDATE

            else -> when {
                !inspect.endpointMatches -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NEEDS_UPDATE
                !isProxyRunning -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.SERVICE_STOPPED
                !running -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_RUNNING
                else -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.MATCHED
            }
        }
        val target = "http://127.0.0.1:$proxyPort"
        return com.yuzhiqiang.antigravity.host.model.HostDetailedStatus(
            type = com.yuzhiqiang.antigravity.host.model.HostType.APP,
            isInstalled = installed,
            isRunning = running,
            integrationState = finalState,
            configurationState = configState,
            configuredEndpoint = inspect.configuredEndpoint ?: target.takeIf { shimReady },
            targetEndpoint = target,
            configPath = "CLOUD_CODE_URL & language_server",
            canEnable = installed,
            canDisable = inspect.canDisable || shimResidue,
            canLaunch = installed && (finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL || (finalState.isReady && isProxyRunning)),
            customPath = customInstallation,
            version = version
        )
    }

    /**
     * 启用 App 代理接入：设置环境变量并安装 Language Server Shim 包装脚本。
     */
    fun enable(proxyPort: Int, customInstallation: String? = null): Boolean {
        // 先完成可验证的 Shim 接入，再写入共享环境变量，避免环境变量成功后留下半成品。
        val previousShimContent = readCurrentShimContent(customInstallation)
        val shimWasReady = isShimReady(customInstallation)
        AppLog.w("Host/App") {
            "enable 开始：port=$proxyPort custom=${customInstallation ?: "<auto>"} shimWasReady=$shimWasReady"
        }
        val shimInstalled = installLanguageServerShim(proxyPort, customInstallation)
        val shimReady = isShimReady(customInstallation)
        if (!shimInstalled || !shimReady) {
            AppLog.e("Host/App") { "enable 失败：shimInstalled=$shimInstalled shimReady=$shimReady" }
            return false
        }

        val envResult = HostOwnershipStore.enableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort = proxyPort
        )
        if (envResult.isSuccess) {
            AppLog.w("Host/App") { "enable 成功：env+shim 均已就绪" }
            return true
        }
        AppLog.e("Host/App", envResult.exceptionOrNull()) { "enable 失败：环境变量写入失败，开始回滚 Shim" }

        if (!shimWasReady) {
            restoreOriginalLanguageServer(customInstallation)
        } else if (previousShimContent != null) {
            restoreShimContent(previousShimContent, customInstallation)
        }
        return false
    }

    /**
     * 禁用 App 代理接入：移除环境变量并还原原始 Language Server 二进制。
     */
    fun disable(customInstallation: String? = null): Boolean {
        val envOk = HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP
        ).isSuccess
        val shimOk = restoreOriginalLanguageServer(customInstallation)
        return envOk && shimOk
    }

    /**
     * 强制重置 App 代理接入至纯净官方模式。
     */
    fun forceReset(customInstallation: String? = null): Boolean {
        val shimOk = restoreOriginalLanguageServer(customInstallation)
        val envOk = HostOwnershipStore.forceResetEnvironment().isSuccess
        return envOk && shimOk
    }

    /**
     * 跨平台启动 Antigravity App。
     */
    fun launch(customInstallation: String? = null, proxyPort: Int? = null): Boolean {
        val env = if (proxyPort != null && isActive(proxyPort, customInstallation)) {
            mapOf("CLOUD_CODE_URL" to ("http://127.0.0.1:" + proxyPort))
        } else {
            null
        }
        val installationPath = customInstallation?.trim()?.takeIf(String::isNotEmpty)
            ?: getCandidateInstallations().firstOrNull(::isInstallationComplete)?.absolutePath
        return HostProcessManager.launch(
            installationPath = installationPath,
            defaultMacApp = "Antigravity",
            defaultWinExe = "Antigravity.exe",
            environment = env
        )
    }

    /**
     * 终止 Antigravity App 及其 language_server 子进程。
     */
    suspend fun terminate(customInstallation: String? = null, force: Boolean = true): Boolean {
        return HostProcessManager.terminateApplication(
            bundleId = "com.google.antigravity",
            matchPatterns = buildProcessPatterns(customInstallation),
            excludePatterns = appExcludePatterns,
            languageServerPatterns = buildLanguageServerPatterns(customInstallation),
            label = "Antigravity App",
            force = force
        )
    }

    /**
     * 跨平台重启 Antigravity App（仅终止与重启 App 自身，绝不干扰 IDE）。
     */
    suspend fun restart(customInstallation: String? = null, proxyPort: Int? = null): Boolean {
        if (!terminate(customInstallation, force = true)) return false
        delay(500)
        if (!launch(customInstallation, proxyPort)) return false
        return waitUntilRunning(customInstallation)
    }

    private fun normalizeCustomInstallation(customInstallation: String): File {
        val customFile = File(customInstallation)
        if (customFile.isDirectory) return customFile

        val appRoot = generateSequence(customFile.parentFile, File::getParentFile)
            .firstOrNull { it.name.endsWith(".app", ignoreCase = true) }
        if (appRoot != null) return appRoot

        // macOS 的文件选择器也可能返回 App 主可执行文件，而测试/便携包不一定带 .app 后缀。
        val macContents = customFile.parentFile?.takeIf { it.name.equals("MacOS", ignoreCase = true) }
            ?.parentFile
            ?.takeIf { it.name.equals("Contents", ignoreCase = true) }
        if (!isWindows && macContents != null) {
            return macContents.parentFile ?: macContents
        }

        // Windows 可执行文件路径的父目录就是安装根目录。
        return customFile.parentFile ?: customFile
    }

    private fun buildProcessPatterns(customInstallation: String?): List<String> {
        val customPath = customInstallation?.trim()?.takeIf(String::isNotEmpty)
        return if (customPath == null) {
            appMatchPatterns
        } else {
            listOf(resolveCanonicalPath(normalizeCustomInstallation(customPath)))
        }
    }

    private fun buildLanguageServerPatterns(customInstallation: String?): List<String> {
        val customRoot = findApplicationRoot(customInstallation)
        if (customRoot == null) return appLanguageServerPatterns
        val relativePath = if (isWindows) {
            "resources/bin/language_server"
        } else {
            "Contents/Resources/bin/language_server"
        }
        return listOf(File(customRoot, relativePath).absolutePath)
    }

    private suspend fun waitUntilRunning(customInstallation: String?): Boolean {
        repeat(25) {
            if (isRunning(customInstallation)) return true
            delay(200)
        }
        return false
    }

    private fun findApplicationRoot(customInstallation: String?): File? {
        val customPath = customInstallation?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return normalizeCustomInstallation(customPath)
    }

    private fun resolveCanonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    private fun isInstallationComplete(root: File): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            isWindows -> root.isDirectory && File(root, "Antigravity.exe").isFile
            os.contains("mac") -> root.isDirectory && File(root, "Contents/MacOS/Antigravity").isFile
            else -> {
                // Linux Electron 应用：检查二进制或 package.json 存在性
                root.isDirectory && (
                        File(root, "antigravity").isFile ||
                                File(root, "resources/app/package.json").isFile
                        )
            }
        }
    }

    private fun discoverMacApplications(bundleId: String): List<File> {
        return try {
            val process = ProcessBuilder(
                "/usr/bin/mdfind", "-0", "kMDItemCFBundleIdentifier == '$bundleId'"
            ).start()
            val output = process.inputStream.readBytes()
            process.waitFor()
            output.toString(Charsets.UTF_8)
                .split('\u0000')
                .filter { it.isNotBlank() }
                .map(::File)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun discoverWindowsInstallations(executableName: String): List<File> {
        if (!isWindows) return emptyList()
        return try {
            listOf(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\$executableName",
                "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\$executableName"
            ).mapNotNull { key ->
                val process = ProcessBuilder("reg", "query", key, "/ve").start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.lineSequence()
                    .firstOrNull { it.contains("REG_SZ") }
                    ?.substringAfter("REG_SZ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::File)
                    ?.let { if (it.isFile) it.parentFile else it }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
