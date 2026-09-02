package com.yuzhiqiang.antigravity.host.app

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.host.ownership.HostOwnershipStore
import com.yuzhiqiang.antigravity.host.process.HostProcessManager
import com.yuzhiqiang.antigravity.host.windows.WindowsShimBinary
import com.yuzhiqiang.antigravity.logging.AppLog
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files

/**
 * Antigravity App 宿主跨平台集成管理器（支持 macOS 与 Windows 双平台）。
 *
 * ## 平台差异与架构设计：
 *
 * 1. **macOS 平台（纯环境变量优先，零权限侵入）**：
 *    - **安装位置与安全模型**：位于 `/Applications/Antigravity.app`，受系统 TCC（透明度、同意和控制）及 Gatekeeper 签名保护。
 *      直接修改 App Bundle 内部文件会触发系统权限拦截（`Operation not permitted`）并破坏 Mach-O 代码签名。
 *    - **接入机制**：通过 `HostOwnershipStore.enableEnvironment` 执行 `launchctl setenv CLOUD_CODE_URL http://127.0.0.1:$port`。
 *      macOS 会自动向当前用户的所有 GUI App 会话广播该环境变量，配合 Studio 在拉起/重启 App 进程时显式注入的环境变量，
 *      即可让官方 `language_server` 自动连接本地代理，**实现 0 权限要求、免弹窗、不破坏签名的极致稳定接入**。
 *
 * 2. **Windows 平台（环境变量 + WindowsShimBinary 增强拦截）**：
 *    - **安装位置与安全模型**：默认安装在 `%LocalAppData%\Programs\Antigravity`，属于用户个人目录，**用户天然具有完整写权限，无权限阻碍**。
 *    - **接入机制**：除写入用户环境变量外，由于 Windows 下 Electron 内部调用 `language_server.exe` 时命令行参数 `--cloud_code_endpoint`
 *      可能优先于未完全广播的系统环境变量，因此 Windows 平台保留 `WindowsShimBinary`（4KB 嵌入式原生 PE 二进制），
 *      在用户目录下安全拦截并重写命令行参数至本地代理端口，保障 100% 精确接管。
 *
 * 3. **自愈与解耦机制**：
 *    - 核心基石始终以 `HostOwnershipStore` 环境变量状态为准；
 *    - 遇到历史版本遗留的 `.original` 备份残留时自动执行保底自愈，绝不造成状态死锁。
 */
object AppHostManager {

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")
    private val isMac = osName.contains("mac")

    /**
     * 检测 Antigravity App 是否已安装。
     */
    fun isInstalled(customInstallation: String? = null): Boolean {
        if (!customInstallation.isNullOrBlank()) {
            val file = File(customInstallation.trim())
            if (file.exists()) {
                if (file.isFile) return true
                if (isInstallationComplete(file) || isShimInstalled(customInstallation)) return true
            }
        }
        return getCandidateInstallations(customInstallation).any { root ->
            isInstallationComplete(root) || isShimInstalled(root.absolutePath)
        }
    }

    private val appMatchPatterns = when {
        isWindows -> listOf("Antigravity.exe")
        isMac -> listOf(
            "Antigravity.app/",
            "/Antigravity.app",
            "Antigravity App.app/",
            "/Antigravity App.app",
            "/MacOS/Antigravity"
        )
        else -> listOf("/antigravity", "antigravity")
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

    private val appLanguageServerPatterns = when {
        isWindows -> listOf(
            "Programs\\Antigravity\\resources\\bin\\language_server.exe",
            "Programs/Antigravity/resources/bin/language_server.exe",
            "Programs\\antigravity\\resources\\bin\\language_server.exe",
            "Programs/antigravity/resources/bin/language_server.exe",
            "Programs\\Antigravity\\resources\\bin\\language_server.original.exe",
            "Programs/Antigravity/resources/bin/language_server.original.exe",
            "Programs\\antigravity\\resources\\bin\\language_server.original.exe",
            "Programs/antigravity/resources/bin/language_server.original.exe"
        )
        isMac -> listOf(
            "Antigravity.app/Contents/Resources/bin/language_server",
            "Antigravity.app/Contents/Resources/bin/language_server.original",
            "Antigravity App.app/Contents/Resources/bin/language_server",
            "Antigravity App.app/Contents/Resources/bin/language_server.original"
        )
        else -> listOf(
            "/antigravity/resources/bin/language_server",
            "/antigravity/resources/bin/language_server.original"
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
        val candidates = if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val standardList = buildList {
                add(File(localAppData, "Programs/Antigravity"))
                add(File(localAppData, "Programs/antigravity"))
                add(File(programFiles, "Antigravity"))
                System.getenv("ProgramFiles(x86)")?.let { add(File(it, "Antigravity")) }
            }
            // 优先直接检测标准路径是否存在，若已命中则直接返回，避免无谓启动 reg.exe 子进程查询注册表
            val existing = standardList.filter(File::exists)
            if (existing.isNotEmpty()) {
                existing
            } else {
                standardList + discoverWindowsInstallations("Antigravity.exe")
            }
        } else if (isMac) {
            val standardList = buildList {
                add(File("/Applications/Antigravity.app"))
                add(File("$userHome/Applications/Antigravity.app"))
                add(File("/Applications/Antigravity App.app"))
                add(File("$userHome/Applications/Antigravity App.app"))
            }
            val existing = standardList.filter(File::exists)
            if (existing.isNotEmpty()) {
                existing
            } else {
                standardList + discoverMacApplications("com.google.antigravity")
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
     * 检测指定文件是否包含 Shim 脚本/二进制标识。
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

    /**
     * 检测指定文件是否是 Studio 托管的 Shim（macOS 脚本或 Windows PE 二进制）。
     */
    private fun isShimFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return if (isWindows) {
            WindowsShimBinary.isShimBinary(file) || isShimScript(file)
        } else {
            isShimScript(file)
        }
    }

    private data class LanguageServerFiles(
        val languageServer: File,
        val original: File,
        val endpointConfig: File,
        val legacyCmdShim: File
    )

    private fun languageServerFiles(root: File): LanguageServerFiles {
        val binDir = if (isWindows) File(root, "resources/bin") else File(root, "Contents/Resources/bin")
        return LanguageServerFiles(
            languageServer = if (isWindows) File(binDir, "language_server.exe") else File(binDir, "language_server"),
            original = if (isWindows) File(binDir, "language_server.original.exe") else File(
                binDir,
                "language_server.original"
            ),
            endpointConfig = File(binDir, "language_server_endpoint.txt"),
            legacyCmdShim = File(binDir, "language_server.cmd")
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
                if (isShimFile(files.languageServer)) return true
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
            if (isWindows) {
                if (files.endpointConfig.exists()) {
                    return runCatching { files.endpointConfig.readText(Charsets.UTF_8) }.getOrNull()
                }
            } else {
                if (isShimScript(files.languageServer)) {
                    return runCatching { files.languageServer.readText(Charsets.UTF_8) }.getOrNull()
                }
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
            if (isWindows) {
                val writeResult = AtomicFileWriter.writeText(
                    target = files.endpointConfig,
                    content = content,
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeResult.isSuccess) restored = true
            } else {
                val writeResult = AtomicFileWriter.writeText(
                    target = files.languageServer,
                    content = content,
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeResult.isFailure) continue
                files.languageServer.setExecutable(true, false)
                restored = true
            }
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
        // 正常接入时也会同时存在 Shim 与 .original 备份；只有缺少可用 Shim
        // 的 .original 才是上一次接入未完成留下的残留。
        return hasOriginalLanguageServer(customInstallation) && !isShimReady(customInstallation)
    }

    private fun copyFile(source: File, target: File): Boolean {
        if (!source.exists() || !source.isFile) return false
        // 1. 优先 APFS 快速克隆 / 原生 Files.copy
        try {
            java.nio.file.Files.copy(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
            )
            if (target.isFile && target.length() == source.length()) {
                return true
            }
        } catch (error: Exception) {
            AppLog.w("Host/App", error) { "Files.copy 快速克隆失败，尝试流式读写降级：${source.absolutePath} -> ${target.absolutePath}" }
        }

        // 2. 一级降级：标准流式通道读写（规避 APFS clonefile 对签名二进制的限制）
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!isWindows) {
                target.setExecutable(source.canExecute(), false)
            }
            if (target.isFile && target.length() == source.length()) {
                return true
            }
        } catch (error: Exception) {
            AppLog.w("Host/App", error) { "流式复制失败，尝试系统原生 cp 命令降级：${source.absolutePath} -> ${target.absolutePath}" }
        }

        // 3. 二级降级：非 Windows 平台调用系统原生 cp 工具
        if (!isWindows) {
            try {
                val process = ProcessBuilder("cp", "-p", source.absolutePath, target.absolutePath).start()
                if (process.waitFor() == 0 && target.isFile && target.length() == source.length()) {
                    return true
                }
            } catch (error: Exception) {
                AppLog.e("Host/App", error) { "系统 cp 命令复制失败：${source.absolutePath} -> ${target.absolutePath}" }
            }
        }

        return false
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
     * 宿主配置写入权限不足异常。
     */
    class HostPermissionDeniedException(
        val hostType: com.yuzhiqiang.antigravity.host.model.HostType,
        val targetPath: String,
        val isMacAppBundle: Boolean = false,
        cause: Throwable? = null
    ) : SecurityException("宿主代理配置写入权限不足：$targetPath", cause)

    private fun isPermissionException(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is java.nio.file.AccessDeniedException || error is SecurityException) return true
        val msg = error.message.orEmpty()
        return msg.contains("Operation not permitted", ignoreCase = true) ||
                msg.contains("Permission denied", ignoreCase = true) ||
                msg.contains("Access is denied", ignoreCase = true) ||
                isPermissionException(error.cause)
    }

    /**
     * 安装 Language Server Shim 包装（macOS Shell 脚本或 Windows PE 二进制），将写死的 --cloud_code_endpoint 动态重写为本地代理端口。
     */
    fun installLanguageServerShim(proxyPort: Int, customInstallation: String? = null): Boolean {
        return installLanguageServerShimDetailed(proxyPort, customInstallation).isSuccess
    }

    /**
     * 安装 Language Server Shim 并返回详细结果/权限异常。
     */
    fun installLanguageServerShimDetailed(proxyPort: Int, customInstallation: String? = null): Result<Unit> {
        val candidates = getCandidateInstallations(customInstallation)
        AppLog.w("Host/App") {
            "安装 Shim：port=$proxyPort custom=${customInstallation ?: "<auto>"} candidates=${candidates.map { it.absolutePath }}"
        }
        var anySuccess = false
        var lastPermissionFailure: HostPermissionDeniedException? = null

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

            if (!binDir.canWrite() && isMac) {
                lastPermissionFailure = HostPermissionDeniedException(
                    hostType = com.yuzhiqiang.antigravity.host.model.HostType.APP,
                    targetPath = files.languageServer.absolutePath,
                    isMacAppBundle = true
                )
            }

            val lsFile = files.languageServer
            val origFile = files.original
            val hadOriginal = origFile.exists()
            val hadShim = isShimFile(lsFile)
            AppLog.w("Host/App") {
                "候选 ${root.absolutePath} ls=${lsFile.exists()}/${lsFile.length()} orig=${origFile.exists()}/${origFile.length()} shim=$hadShim"
            }
            val previousShimContent = if (hadShim) {
                readCurrentShimContent(customInstallation)
            } else {
                null
            }

            // 1. 备份原生二进制。macOS 优先复制，避免 rename 正在/刚退出的可执行文件触发 ETXTBSY。
            if (!origFile.exists()) {
                if (!lsFile.exists() || isShimFile(lsFile)) {
                    AppLog.e("Host/App") {
                        "无法备份 language_server：exists=${lsFile.exists()} isShim=${isShimFile(lsFile)} path=${lsFile.absolutePath}"
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
                    if (!binDir.canWrite()) {
                        lastPermissionFailure = HostPermissionDeniedException(
                            hostType = com.yuzhiqiang.antigravity.host.model.HostType.APP,
                            targetPath = lsFile.absolutePath,
                            isMacAppBundle = isMac
                        )
                    }
                    continue
                }
            }
            if (!origFile.exists()) {
                AppLog.e("Host/App") { "备份后仍缺少 original：${origFile.absolutePath}" }
                continue
            }

            // 2. 安装包装脚本/二进制，避免 App 进程读取到半截文件
            val targetEndpoint = "http://127.0.0.1:$proxyPort"
            val ok = if (isWindows) {
                val writeEndpointResult = AtomicFileWriter.writeText(
                    target = files.endpointConfig,
                    content = targetEndpoint,
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeEndpointResult.isFailure) {
                    val ex = writeEndpointResult.exceptionOrNull()
                    if (isPermissionException(ex)) {
                        lastPermissionFailure = HostPermissionDeniedException(
                            hostType = com.yuzhiqiang.antigravity.host.model.HostType.APP,
                            targetPath = files.endpointConfig.absolutePath,
                            isMacAppBundle = false,
                            cause = ex
                        )
                    }
                    AppLog.e("Host/App", ex) {
                        "写入 Windows Shim endpoint 失败：${files.endpointConfig.absolutePath}"
                    }
                }
                val writeShimOk = WindowsShimBinary.writeShimBinary(lsFile)
                if (!writeShimOk) {
                    AppLog.e("Host/App") { "写入 Windows Shim 二进制失败：${lsFile.absolutePath}" }
                }
                if (files.legacyCmdShim.exists()) {
                    files.legacyCmdShim.delete()
                }
                writeEndpointResult.isSuccess && writeShimOk && isShimFile(lsFile)
            } else {
                val scriptContent = buildMacShimScript(targetEndpoint)
                val writeResult = AtomicFileWriter.writeText(
                    target = lsFile,
                    content = scriptContent + "\n",
                    permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING,
                    disallowSymlinks = true
                )
                if (writeResult.isFailure) {
                    val ex = writeResult.exceptionOrNull()
                    if (isPermissionException(ex) || !binDir.canWrite()) {
                        lastPermissionFailure = HostPermissionDeniedException(
                            hostType = com.yuzhiqiang.antigravity.host.model.HostType.APP,
                            targetPath = lsFile.absolutePath,
                            isMacAppBundle = true,
                            cause = ex
                        )
                    }
                    AppLog.e("Host/App", ex) {
                        "写入 macOS Shim 失败：${lsFile.absolutePath}"
                    }
                }
                val executable = lsFile.setExecutable(true, false) || lsFile.canExecute()
                var macShimOk = writeResult.isSuccess && executable && isShimScript(lsFile)
                if (!macShimOk) {
                    AppLog.w("Host/App") { "macOS 普通写入权限受限，尝试通过原生管理员提权安装 Shim：${lsFile.absolutePath}" }
                    macShimOk = installShimWithMacAdminPrivileges(files, scriptContent)
                }
                macShimOk
            }
            if (ok) {
                anySuccess = true
                continue
            }

            // 写入失败时撤销本轮备份或保底恢复原生二进制，避免只剩 original 导致应用损坏与状态死锁。
            if (isWindows) {
                if (files.endpointConfig.exists()) files.endpointConfig.delete()
                if (files.legacyCmdShim.exists()) files.legacyCmdShim.delete()
            }
            if (lsFile.exists() && isShimFile(lsFile)) {
                lsFile.delete()
            }
            if (!hadOriginal && origFile.exists()) {
                when {
                    !lsFile.exists() -> {
                        moveOrReplaceFile(origFile, lsFile)
                        if (!isWindows) lsFile.setExecutable(true, false)
                    }
                    !isShimFile(lsFile) -> origFile.delete()
                }
            } else if (hadShim && previousShimContent != null) {
                restoreShimContent(previousShimContent, customInstallation)
            }
            // 保底自愈：若当前依然缺失主二进制但存在备份，无论先前状态如何，强制还原原生二进制
            if (!lsFile.exists() && origFile.exists()) {
                val recovered = moveOrReplaceFile(origFile, lsFile)
                if (recovered && !isWindows) {
                    lsFile.setExecutable(true, false)
                }
                AppLog.w("Host/App") { "安装 Shim 失败，已保底自愈还原原生二进制：recovered=$recovered path=${lsFile.absolutePath}" }
            }
        }

        return if (anySuccess) {
            Result.success(Unit)
        } else if (lastPermissionFailure != null) {
            Result.failure(lastPermissionFailure)
        } else {
            Result.failure(IllegalStateException("未能成功安装 Language Server Shim 包装"))
        }
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

            val files = languageServerFiles(root)
            if (isWindows) {
                if (files.endpointConfig.exists()) {
                    files.endpointConfig.delete()
                }
                if (files.legacyCmdShim.exists()) {
                    files.legacyCmdShim.delete()
                }
            }

            val lsFile = files.languageServer
            val origFile = files.original

            // 2. 如果 lsFile 存在且是 shim，则删除 shim
            if (lsFile.exists() && isShimFile(lsFile)) {
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
                } else if (!isShimFile(lsFile)) {
                    // lsFile 存在且是正常二进制，origFile 只是多余备份，直接清理
                    origFile.delete()
                    anyRestoredOrClean = true
                }
            } else if (lsFile.exists() && !isShimFile(lsFile)) {
                anyRestoredOrClean = true
            }

            if (!anyRestoredOrClean && isMac && (origFile.exists() || isShimFile(lsFile))) {
                AppLog.w("Host/App") { "macOS 普通权限还原失败，尝试通过原生管理员提权还原：${lsFile.absolutePath}" }
                if (restoreWithMacAdminPrivileges(files)) {
                    anyRestoredOrClean = true
                }
            }
        }
        return anyRestoredOrClean || candidates.none { it.exists() }
    }

    private fun installShimWithMacAdminPrivileges(
        files: LanguageServerFiles,
        scriptContent: String
    ): Boolean {
        if (!isMac) return false
        val tempScript = createSecureTempScript("agy_shim_", scriptContent + "\n") ?: return false
        return try {
            val exitCode = runMacAdminScript(
                """
                set -eu
                source=${shellQuote(tempScript.absolutePath)}
                target=${shellQuote(files.languageServer.absolutePath)}
                original=${shellQuote(files.original.absolutePath)}
                parent=${shellQuote(files.languageServer.parentFile.absolutePath)}
                [ -d "${'$'}parent" ] || exit 1
                [ ! -L "${'$'}target" ] || exit 1
                [ ! -L "${'$'}original" ] || exit 1
                if [ ! -f "${'$'}original" ]; then
                    [ -f "${'$'}target" ] || exit 1
                    /bin/cp -p "${'$'}target" "${'$'}original" || exit 1
                fi
                staged="${'$'}(/usr/bin/mktemp "${'$'}parent/.language_server.XXXXXX")" || exit 1
                trap '/bin/rm -f "${'$'}staged"' EXIT
                /bin/cp "${'$'}source" "${'$'}staged" || exit 1
                /bin/chmod 755 "${'$'}staged" || exit 1
                /bin/mv -f "${'$'}staged" "${'$'}target" || exit 1
                trap - EXIT
                """.trimIndent()
            )
            exitCode == 0 && isShimScript(files.languageServer)
        } catch (error: Exception) {
            AppLog.e("Host/App", error) { "通过 macOS 管理员提权安装 Shim 失败" }
            false
        } finally {
            tempScript.delete()
        }
    }

    private fun restoreWithMacAdminPrivileges(files: LanguageServerFiles): Boolean {
        if (!isMac) return false
        return try {
            val exitCode = runMacAdminScript(
                """
                set -eu
                target=${shellQuote(files.languageServer.absolutePath)}
                original=${shellQuote(files.original.absolutePath)}
                parent=${shellQuote(files.languageServer.parentFile.absolutePath)}
                [ -d "${'$'}parent" ] || exit 1
                [ ! -L "${'$'}target" ] || exit 1
                [ ! -L "${'$'}original" ] || exit 1
                if [ -f "${'$'}original" ]; then
                    staged="${'$'}(/usr/bin/mktemp "${'$'}parent/.language_server.XXXXXX")" || exit 1
                    trap '/bin/rm -f "${'$'}staged"' EXIT
                    /bin/cp -p "${'$'}original" "${'$'}staged" || exit 1
                    /bin/chmod 755 "${'$'}staged" || exit 1
                    /bin/mv -f "${'$'}staged" "${'$'}target" || exit 1
                    trap - EXIT
                    /bin/rm -f "${'$'}original"
                elif [ -f "${'$'}target" ]; then
                    if /usr/bin/grep -q "ANTIGRAVITY_STUDIO_MANAGED_SHIM" "${'$'}target"; then
                        /bin/rm -f "${'$'}target"
                    fi
                fi
                """.trimIndent()
            )
            exitCode == 0
        } catch (error: Exception) {
            AppLog.e("Host/App", error) { "通过 macOS 管理员提权还原原生文件失败" }
            false
        }
    }

    internal fun createSecureTempScript(prefix: String, content: String): File? {
        return runCatching {
            Files.createTempFile(prefix, ".sh").toFile().apply {
                writeText(content, Charsets.UTF_8)
                setReadable(false, false)
                setWritable(false, false)
                check(setReadable(true, true) || canRead())
                check(setWritable(true, true) || canWrite())
            }
        }.onFailure { error ->
            AppLog.e("Host/App", error) { "创建 macOS 管理员临时脚本失败" }
        }.getOrNull()
    }

    private fun runMacAdminScript(script: String): Int {
        val process = ProcessBuilder(macAdminCommand(script)).start()
        return process.waitFor()
    }

    internal fun macAdminCommand(script: String): List<String> = listOf(
        "/usr/bin/osascript",
        "-e", "on run argv",
        "-e", "do shell script (item 1 of argv) with administrator privileges",
        "-e", "end run",
        "--",
        script
    )

    internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

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
        val isAppManaged = environment.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED && environment.endpointMatches
        val isShimActive = isShimReady(customInstallation)
        return isAppManaged || isShimActive
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
            !installed -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL
            // 0. Shim 残留（只存在 .original 备份或未还原） -> MISMATCH
            shimResidue -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH
            // 1. 环境变量由 APP 托管且端点匹配 -> MANAGED
            inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED && inspect.endpointMatches ->
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED
            // 2. 外部环境变量接管且端点匹配，且 Shim 就绪 -> EXTERNAL
            shimReady && inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.EXTERNAL && inspect.endpointMatches ->
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.EXTERNAL
            // 3. Shim 就绪且端点匹配 -> MANAGED
            shimReady && inspect.endpointMatches -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED
            // 4. 端点失配且存在 Shim
            shimReady && !inspect.endpointMatches ->
                com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH
            // 5. 其余情况为官方直连模式
            else -> com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL
        }

        val configState = when {
            !installed -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
            finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL ->
                com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_ENABLED
            finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.CONFLICT ||
                    finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.UNAVAILABLE ->
                com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.UNAVAILABLE
            finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MISMATCH ->
                com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NEEDS_UPDATE
            !isProxyRunning -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.SERVICE_STOPPED
            !running -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.NOT_RUNNING
            else -> com.yuzhiqiang.antigravity.host.model.ClientConfigurationState.MATCHED
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
            configPath = "CLOUD_CODE_URL",
            canEnable = installed,
            canDisable = (inspect.canDisable && inspect.state == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.MANAGED) || shimResidue || shimReady,
            canLaunch = installed && (finalState == com.yuzhiqiang.antigravity.host.model.ClientIntegrationState.OFFICIAL || (finalState.isReady && isProxyRunning)),
            customPath = customInstallation,
            version = version
        )
    }

    /**
     * 启用 App 代理接入：
     * - macOS：通过 launchctl 设置用户全局环境变量 CLOUD_CODE_URL，零权限、免弹窗直接生效；
     * - Windows：设置用户环境变量并写入 WindowsShimBinary 精确拦截命令行参数。
     */
    fun enable(proxyPort: Int, customInstallation: String? = null): Boolean {
        return enableDetailed(proxyPort, customInstallation).isSuccess
    }

    /**
     * 启用 App 代理接入并返回详细结果。
     *
     * 核心步骤：
     * 1. 设置系统共享环境变量（macOS launchctl / Windows 注册表），作为接入的核心基石；
     * 2. 尽力尝试安装 Shim 包装器（Windows 下写入用户目录 100% 成功；macOS 若权限受限则自动跳过，绝不阻断）；
     * 3. 进程拉起/重启时由 Studio 显式注入 CLOUD_CODE_URL 环境变量提供双重保证。
     */
    fun enableDetailed(proxyPort: Int, customInstallation: String? = null): Result<Unit> {
        AppLog.w("Host/App") {
            "enable 开始：port=$proxyPort custom=${customInstallation ?: "<auto>"}"
        }
        // 1. 设置环境变量（核心基石，零权限要求）
        val envResult = HostOwnershipStore.enableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP,
            proxyPort = proxyPort
        )
        if (envResult.isFailure) {
            AppLog.e("Host/App", envResult.exceptionOrNull()) { "enable 失败：环境变量写入失败" }
            return envResult
        }

        // 2. 尽力安装 Shim 包装（Windows 用户目录无障碍写入；macOS 仅作非阻塞尝试）
        runCatching {
            installLanguageServerShim(proxyPort, customInstallation)
        }.onFailure {
            AppLog.w("Host/App", it) { "尽力安装 Shim 失败，跳过 Shim 保持环境变量模式生效" }
        }

        AppLog.w("Host/App") { "enable 成功：环境变量模式已就绪" }
        return Result.success(Unit)
    }

    /**
     * 禁用 App 代理接入：移除环境变量并还原原始 Language Server 二进制。
     */
    fun disable(customInstallation: String? = null): Boolean {
        val envOk = HostOwnershipStore.disableEnvironment(
            owner = HostOwnershipStore.EnvironmentOwner.APP
        ).isSuccess
        runCatching { restoreOriginalLanguageServer(customInstallation) }
        return envOk
    }

    /**
     * 强制重置 App 代理接入至纯净官方模式。
     */
    fun forceReset(customInstallation: String? = null): Boolean {
        val envOk = HostOwnershipStore.forceResetEnvironment().isSuccess
        runCatching { restoreOriginalLanguageServer(customInstallation) }
        return envOk
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
        delay(150)
        if (!launch(customInstallation, proxyPort)) return false
        return waitUntilRunning(customInstallation)
    }

    private fun normalizeCustomInstallation(customInstallation: String): File {
        val customFile = File(customInstallation.trim())
        val appRoot = generateSequence(customFile) { it.parentFile }
            .firstOrNull { it.name.endsWith(".app", ignoreCase = true) }
        if (appRoot != null) return appRoot

        if (customFile.isDirectory) return customFile

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
        repeat(30) {
            if (isRunning(customInstallation)) return true
            delay(100)
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
        if (!root.exists() || !root.isDirectory) return false
        return when {
            isWindows -> File(root, "Antigravity.exe").isFile || File(root, "_/Antigravity.exe").isFile
            else -> File(root, "Contents/MacOS/Antigravity").isFile ||
                    File(root, "Contents/MacOS/Antigravity App").isFile ||
                    File(root, "Contents/MacOS/Electron").isFile ||
                    File(root, "antigravity").isFile
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
                    .firstOrNull { it.contains("REG_SZ") || it.contains("REG_EXPAND_SZ") }
                    ?.let { line ->
                        line.substringAfter("REG_EXPAND_SZ", "")
                            .ifBlank { line.substringAfter("REG_SZ", "") }
                            .trim()
                    }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::File)
                    ?.let { if (it.isFile) it.parentFile else it }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
