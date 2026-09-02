package com.yuzhiqiang.antigravity.proxy.server

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.core.platform.AppDataPaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.SecureRandom
import java.util.Base64

/** 持久化 Studio 本地代理的 256-bit 路径访问令牌。 */
class ProxyAccessTokenStore(
    private val tokenFile: File = AppDataPaths.resolve(FILE_NAME)
) {
    @Synchronized
    fun loadOrCreate(): Result<String> = runCatching {
        val path = tokenFile.toPath()
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            check(!Files.isSymbolicLink(path)) { "代理访问令牌文件不能是符号链接" }
            AtomicFileWriter.setOwnerOnlyPermissions(tokenFile).getOrThrow()
            tokenFile.readText(Charsets.UTF_8).trim().takeIf(::isValidToken)?.let { return Result.success(it) }
        }

        val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        AtomicFileWriter.writeText(
            target = tokenFile,
            content = "$token\n",
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true
        ).getOrThrow()
        token
    }

    companion object {
        const val FILE_NAME = "proxy-access-token"
        private const val TOKEN_BYTES = 32
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{43}")

        internal fun isValidToken(token: String): Boolean = TOKEN_PATTERN.matches(token)
    }
}
