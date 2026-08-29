package com.yuzhiqiang.antigravity.data.storage

import com.yuzhiqiang.antigravity.core.file.AtomicFileWriter
import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import com.yuzhiqiang.antigravity.services.auth.HostAccountDetector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 管理 App 与外部宿主共用的官方 OAuth 凭据文件。
 *
 * 该类只负责外部凭据文件，不持有 Studio 账号列表状态；账号状态仍由 [AccountStore] 管理。
 */
internal class OfficialCredentialsStore(
    private val customRootDir: File? = null
) {
    internal data class Snapshot(
        val files: List<FileSnapshot>
    ) : AutoCloseable {
        override fun close() {
            files.forEach(FileSnapshot::close)
        }
    }

    internal data class FileSnapshot(
        val file: File,
        val existed: Boolean,
        val originalBytes: ByteArray
    ) : AutoCloseable {
        override fun close() {
            originalBytes.fill(0)
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun importAccount(): AccountInfo? {
        val file = primaryFile()
        if (!file.exists()) return null
        return try {
            val element = json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
                ?: return null
            val accessToken = element["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
            val refreshToken = element["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
            val email = element["email"]?.jsonPrimitive?.contentOrNull
                ?: element["user_email"]?.jsonPrimitive?.contentOrNull
                ?: "default-user@antigravity"
            val name = element["name"]?.jsonPrimitive?.contentOrNull
            val expiryTimestamp = resolveExpiryTimestamp(element)
            val tokenType = element["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
            val idToken = element["id_token"]?.jsonPrimitive?.contentOrNull

            AccountInfo(
                id = "acc_${email.hashCode().toUInt().toString(16)}",
                profile = AccountProfile(
                    email = email,
                    name = name
                ),
                tokens = OAuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiryTimestamp = expiryTimestamp,
                    tokenType = tokenType,
                    idToken = idToken
                ),
                isActive = true,
                status = AccountStatus.ACTIVE
            )
        } catch (_: Exception) {
            null
        }
    }

    fun sync(account: AccountInfo, snapshot: Snapshot): Boolean {
        return try {
            val fields = readCredentialFields(primaryFile())
            val expiryTimestamp = normalizeEpochSeconds(account.tokens.expiryTimestamp)

            fields["access_token"] = JsonPrimitive(account.tokens.accessToken)
            fields["refresh_token"] = JsonPrimitive(account.tokens.refreshToken)
            fields["email"] = JsonPrimitive(account.email)
            fields["name"] = JsonPrimitive(account.profile.name ?: "")
            fields["expiry_timestamp"] = JsonPrimitive(expiryTimestamp)
            fields["expiry_date"] = JsonPrimitive(expiryTimestamp * MILLIS_PER_SECOND)
            fields["token_type"] = JsonPrimitive(account.tokens.tokenType)
            if ("user_email" in fields) {
                fields["user_email"] = JsonPrimitive(account.email)
            }
            fields["antigravity_cockpit_active_email"] = JsonPrimitive(account.email)

            val idToken = account.tokens.idToken?.takeIf { it.isNotBlank() }
            if (idToken != null) {
                fields["id_token"] = JsonPrimitive(idToken)
            } else {
                fields.remove("id_token")
            }

            val content = json.encodeToString(JsonObject.serializer(), JsonObject(fields))
            snapshot.files.forEach { target ->
                writeSensitiveText(target.file, content)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun captureSnapshot(targetFiles: List<File>? = null): Result<Snapshot> {
        return try {
            val seenPaths = mutableSetOf<String>()
            val plannedFiles = (targetFiles ?: resolveTargetFiles()).filter { file ->
                val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
                seenPaths.add(canonicalPath)
            }
            val snapshots = plannedFiles.map { file ->
                val existed = file.exists()
                FileSnapshot(
                    file = file,
                    existed = existed,
                    originalBytes = if (existed) file.readBytes() else byteArrayOf()
                )
            }
            Result.success(Snapshot(snapshots))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun restoreSnapshot(snapshot: Snapshot): Boolean {
        var restored = true
        snapshot.files.asReversed().forEach { fileSnapshot ->
            val fileRestored = try {
                if (fileSnapshot.existed) {
                    writeSensitiveBytes(fileSnapshot.file, fileSnapshot.originalBytes)
                    true
                } else {
                    !fileSnapshot.file.exists() ||
                            (fileSnapshot.file.isFile && fileSnapshot.file.delete())
                }
            } catch (_: Exception) {
                false
            }
            restored = restored && fileRestored
        }
        return restored
    }

    fun primaryFile(): File {
        if (customRootDir != null) {
            return File(customRootDir, "oauth_credentials.json")
        }
        val customDataDir = System.getenv("ANTIGRAVITY_DATA_DIR")
            ?: System.getenv("GEMINI_DATA_DIR")
        if (!customDataDir.isNullOrBlank()) {
            return File(customDataDir, "oauth_credentials.json")
        }
        return File(System.getProperty("user.home"), ".gemini/oauth_creds.json")
    }

    private fun resolveTargetFiles(): List<File> {
        val officialFile = primaryFile()
        if (customRootDir != null) {
            return listOf(officialFile)
        }

        val userHome = System.getProperty("user.home")
        val candidates = listOf(
            officialFile,
            File(userHome, ".gemini/oauth_creds.json"),
            File(userHome, ".gemini/oauth_credentials.json"),
            File(userHome, ".gemini/antigravity/oauth_credentials.json"),
            HostAccountDetector.resolvePlatformAppCredentialsFile()
        )
        val seenPaths = mutableSetOf<String>()
        return candidates.filter { file ->
            val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            val shouldWrite = file == officialFile || file.exists() || file.parentFile?.exists() == true
            shouldWrite && seenPaths.add(canonicalPath)
        }
    }

    private fun readCredentialFields(file: File): MutableMap<String, JsonElement> {
        if (!file.exists()) {
            return mutableMapOf()
        }
        return try {
            val existing = json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
            existing?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun resolveExpiryTimestamp(element: JsonObject): Long {
        val rawTimestamp = element["expiry_timestamp"]?.jsonPrimitive?.longOrNull
            ?: element["expiry_date"]?.jsonPrimitive?.longOrNull
            ?: (System.currentTimeMillis() / MILLIS_PER_SECOND + DEFAULT_TOKEN_LIFETIME_SECONDS)
        return normalizeEpochSeconds(rawTimestamp)
    }

    private fun normalizeEpochSeconds(timestamp: Long): Long {
        return if (timestamp >= EPOCH_MILLIS_THRESHOLD) {
            timestamp / MILLIS_PER_SECOND
        } else {
            timestamp
        }
    }

    private fun writeSensitiveText(file: File, content: String) {
        writeSensitiveBytes(file, content.toByteArray(Charsets.UTF_8))
    }

    private fun writeSensitiveBytes(file: File, content: ByteArray) {
        AtomicFileWriter.writeBytes(
            target = file,
            content = content,
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true
        ).getOrThrow()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val EPOCH_MILLIS_THRESHOLD = 10_000_000_000L
        const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3_600L
    }
}
