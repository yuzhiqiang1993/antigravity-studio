package com.yuzhiqiang.antigravity.services.auth

import com.yuzhiqiang.antigravity.domain.model.account.AccountInfo
import com.yuzhiqiang.antigravity.domain.model.account.AccountProfile
import com.yuzhiqiang.antigravity.domain.model.account.AccountStatus
import com.yuzhiqiang.antigravity.domain.model.account.AccountTier
import com.yuzhiqiang.antigravity.domain.model.account.OAuthTokens
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.yuzhiqiang.antigravity.core.platform.DesktopPlatformService
import java.net.ServerSocket
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
data class GoogleTokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 3600L,
    val token_type: String = "Bearer",
    val id_token: String? = null
)

@Serializable
data class GoogleUserInfo(
    val email: String,
    val name: String? = null,
    val picture: String? = null
)

class GoogleAuthService {

    companion object {
        const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
        const val CLIENT_SECRET = "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"

        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"

        val OAUTH_SCOPES = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                proxySelector(com.yuzhiqiang.antigravity.network.PlatformNetworkConfig.createSmartProxySelector())
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000L
            requestTimeoutMillis = 30_000L
        }
    }

    private var currentOAuthDeferred: CompletableDeferred<String>? = null
    private var currentOAuthServer: EmbeddedServer<*, *>? = null

    fun submitManualCallback(urlOrCode: String): Boolean {
        val trimmed = urlOrCode.trim()
        if (trimmed.isEmpty()) return false

        val code = when {
            trimmed.contains("code=") -> {
                val params = trimmed.substringAfter("?").split("&")
                params.firstOrNull { it.startsWith("code=") }
                    ?.removePrefix("code=")
                    ?.substringBefore("&")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            }

            else -> trimmed
        }

        if (!code.isNullOrBlank()) {
            val deferred = currentOAuthDeferred
            if (deferred != null && deferred.isActive) {
                deferred.complete(code)
                return true
            }
        }
        return false
    }

    fun cancelOAuthFlow() {
        currentOAuthDeferred?.completeExceptionally(java.util.concurrent.CancellationException("用户取消了授权"))
        currentOAuthDeferred = null
        try {
            currentOAuthServer?.stop(200, 500)
        } catch (_: Exception) {
        }
        currentOAuthServer = null
    }

    /**
     * 启动完整的 Google OAuth 2.0 浏览器授权流程
     */
    suspend fun startOAuthFlow(
        openBrowserDirectly: Boolean = true,
        onAuthUrlReady: ((authUrl: String) -> Unit)? = null
    ): Result<AccountInfo> = withContext(Dispatchers.IO) {
        val port = findAvailableCallbackPort(41321, 41350)
            ?: return@withContext Result.failure(IllegalStateException("无法找到可用的 OAuth 本地回调端口"))

        val redirectUri = "http://127.0.0.1:$port/oauth2callback"
        val state = generateRandomString(24)
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val scopeParam = URLEncoder.encode(OAUTH_SCOPES.joinToString(" "), "UTF-8")
        val authUrl = "$AUTH_URL?client_id=$CLIENT_ID" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&response_type=code" +
                "&scope=$scopeParam" +
                "&state=$state" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256" +
                "&access_type=offline" +
                "&prompt=consent"

        val codeDeferred = CompletableDeferred<String>()
        currentOAuthDeferred = codeDeferred
        var server: EmbeddedServer<*, *>? = null

        try {
            server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/oauth2callback") {
                        val receivedState = call.request.queryParameters["state"]
                        val code = call.request.queryParameters["code"]
                        val error = call.request.queryParameters["error"]

                        if (error != null) {
                            call.respondText(
                                buildCallbackHtml(false, "授权取消或失败: $error"),
                                ContentType.Text.Html,
                                HttpStatusCode.BadRequest
                            )
                            codeDeferred.completeExceptionally(IllegalStateException("Google 授权失败: $error"))
                            return@get
                        }

                        if (receivedState != state || code.isNullOrBlank()) {
                            call.respondText(
                                buildCallbackHtml(false, "State 校验不匹配或缺失 Code"),
                                ContentType.Text.Html,
                                HttpStatusCode.BadRequest
                            )
                            codeDeferred.completeExceptionally(IllegalStateException("OAuth State 校验失败"))
                            return@get
                        }

                        call.respondText(
                            buildCallbackHtml(true, "授权成功！您可以关闭此网页并返回 Antigravity Studio。"),
                            ContentType.Text.Html,
                            HttpStatusCode.OK
                        )
                        codeDeferred.complete(code)
                    }
                }
            }.start(wait = false)

            onAuthUrlReady?.invoke(authUrl)
            if (openBrowserDirectly) {
                openBrowser(authUrl)
            }

            val code = withTimeoutOrNull(180_000L) {
                codeDeferred.await()
            } ?: return@withContext Result.failure(IllegalStateException("等待浏览器授权超时（3分钟）"))

            val tokens = exchangeCodeForTokens(code, redirectUri, codeVerifier).getOrElse {
                return@withContext Result.failure(it)
            }
            val userInfo = fetchUserInfo(tokens.accessToken).getOrElse {
                return@withContext Result.failure(it)
            }

            val accountInfo = AccountInfo(
                id = "acc_${userInfo.email.hashCode().toUInt().toString(16)}",
                profile = AccountProfile(
                    email = userInfo.email,
                    name = userInfo.name,
                    avatarUrl = userInfo.picture,
                    tier = AccountTier.FREE
                ),
                tokens = tokens,
                isActive = true,
                status = AccountStatus.ACTIVE
            )
            Result.success(accountInfo)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            server?.stop(500, 1000)
        }
    }

    /**
     * 手动通过 Refresh Token 导入单个账号并拉取用户信息
     */
    suspend fun importViaRefreshToken(
        refreshToken: String,
        fallbackEmail: String? = null,
        fallbackName: String? = null,
        customNote: String? = null
    ): Result<AccountInfo> = withContext(Dispatchers.IO) {
        val cleanToken = refreshToken.trim()
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Refresh Token 不能为空"))
        }

        val refreshResult = refreshAccessToken(cleanToken).getOrElse {
            return@withContext Result.failure(it)
        }
        val userInfoResult = fetchUserInfo(refreshResult.accessToken)
        val email = userInfoResult.getOrNull()?.email ?: fallbackEmail
        if (email.isNullOrBlank()) {
            return@withContext Result.failure(
                userInfoResult.exceptionOrNull() ?: IllegalStateException("未能获取到该 Token 对应的 Google 账号邮箱")
            )
        }

        val name = userInfoResult.getOrNull()?.name ?: fallbackName
        val picture = userInfoResult.getOrNull()?.picture

        val accountInfo = AccountInfo(
            id = "acc_${email.hashCode().toUInt().toString(16)}",
            profile = AccountProfile(
                email = email,
                name = name,
                avatarUrl = picture,
                tier = AccountTier.FREE
            ),
            tokens = refreshResult,
            isActive = true,
            status = AccountStatus.ACTIVE,
            customNote = customNote
        )
        Result.success(accountInfo)
    }

    /**
     * 批量导入账号（完全对齐 Cockpit 插件：支持多行 Token、JSON 数组、备份 JSON 及混合格式，并按 6 并发抓取）
     */
    suspend fun importBatch(rawInput: String): List<Result<AccountInfo>> = withContext(Dispatchers.IO) {
        val entries = RefreshTokenParser.parse(rawInput)
        if (entries.isEmpty()) {
            return@withContext listOf(Result.failure(IllegalArgumentException("未能从输入中识别出有效的 Refresh Token 或账号 JSON 数据")))
        }

        val semaphore = Semaphore(6)
        coroutineScope {
            entries.map { entry ->
                async {
                    semaphore.withPermit {
                        importViaRefreshToken(
                            refreshToken = entry.token,
                            fallbackEmail = entry.email,
                            fallbackName = entry.name,
                            customNote = entry.customNote
                        )
                    }
                }
            }.awaitAll()
        }
    }


    /**
     * 用 Authorization Code 换取 Access Token 和 Refresh Token
     */
    suspend fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        codeVerifier: String
    ): Result<OAuthTokens> = withContext(Dispatchers.IO) {
        try {
            val responseText: String = httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("client_id", CLIENT_ID)
                    append("client_secret", CLIENT_SECRET)
                    append("code", code)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                }
            ).body()

            val root = json.parseToJsonElement(responseText) as? JsonObject
                ?: return@withContext Result.failure(IllegalStateException("Google Token 响应非合法 JSON"))

            val errorMsg = root["error_description"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
            if (errorMsg != null) {
                return@withContext Result.failure(IllegalStateException("Google 授权失败: $errorMsg"))
            }

            val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.failure(IllegalStateException("响应中缺少 access_token"))
            val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.failure(IllegalStateException("未收到 Refresh Token，请确保授权时勾选了相关权限"))
            val expiresIn = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
            val tokenType = root["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
            val idToken = root["id_token"]?.jsonPrimitive?.contentOrNull

            val expiryTimestamp = System.currentTimeMillis() / 1000L + expiresIn
            Result.success(
                OAuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiryTimestamp = expiryTimestamp,
                    tokenType = tokenType,
                    idToken = idToken
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "换取 OAuth Token 失败", e))
        }
    }

    /**
     * 使用 Refresh Token 刷新获取新的 Access Token
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<OAuthTokens> = withContext(Dispatchers.IO) {
        try {
            val responseText: String = httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("client_id", CLIENT_ID)
                    append("client_secret", CLIENT_SECRET)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                }
            ).body()

            val root = json.parseToJsonElement(responseText) as? JsonObject
                ?: return@withContext Result.failure(IllegalStateException("Google Token 响应非合法 JSON"))

            val errorMsg = root["error_description"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
            if (errorMsg != null) {
                return@withContext Result.failure(IllegalStateException("Token 已失效或已被 Google 撤销 ($errorMsg)"))
            }

            val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.failure(IllegalStateException("Google 响应中缺少 access_token"))
            val newRefreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refreshToken
            val expiresIn = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
            val tokenType = root["token_type"]?.jsonPrimitive?.contentOrNull ?: "Bearer"
            val idToken = root["id_token"]?.jsonPrimitive?.contentOrNull


            val expiryTimestamp = System.currentTimeMillis() / 1000L + expiresIn
            Result.success(
                OAuthTokens(
                    accessToken = accessToken,
                    refreshToken = newRefreshToken,
                    expiryTimestamp = expiryTimestamp,
                    tokenType = tokenType,
                    idToken = idToken
                )
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "刷新 Token 失败", e))
        }
    }

    /**
     * 获取 Google 用户信息 (Email, Name, Picture)
     */
    suspend fun fetchUserInfo(accessToken: String): Result<GoogleUserInfo> = withContext(Dispatchers.IO) {
        try {
            val responseText: String = httpClient.get(USERINFO_URL) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }.body()

            val root = json.parseToJsonElement(responseText) as? JsonObject
                ?: return@withContext Result.failure(IllegalStateException("UserInfo 响应非合法 JSON"))

            val errorMsg = root["error_description"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
            if (errorMsg != null) {
                return@withContext Result.failure(IllegalStateException("获取用户资料失败: $errorMsg"))
            }

            val email = root["email"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.failure(IllegalStateException("未能从 Google 响应中解析出 Email"))
            val name = root["name"]?.jsonPrimitive?.contentOrNull
            val picture = root["picture"]?.jsonPrimitive?.contentOrNull

            Result.success(GoogleUserInfo(email, name, picture))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "获取用户信息失败", e))
        }
    }


    private fun findAvailableCallbackPort(startPort: Int, endPort: Int): Int? {
        for (port in startPort..endPort) {
            try {
                ServerSocket(port, 10, java.net.InetAddress.getByName("127.0.0.1")).use {
                    return it.localPort
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun generateRandomString(bytesCount: Int): String {
        val random = SecureRandom()
        val bytes = ByteArray(bytesCount)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeVerifier(): String {
        return generateRandomString(32)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    fun openBrowser(url: String) {
        DesktopPlatformService.openBrowser(url)
    }

    private fun buildCallbackHtml(success: Boolean, message: String): String {
        val bgColor = "#12141c"
        val cardBg = "#1a1d28"
        val textColor = "#ffffff"
        val subColor = "#9aa0a6"
        val accentColor = if (success) "#00E676" else "#FF5252"
        val icon = if (success) "✓" else "✕"
        val title = if (success) "授权成功" else "授权失败"

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title - Antigravity Studio</title>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background: $bgColor;
                        color: $textColor;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                    }
                    .card {
                        background: $cardBg;
                        border: 1px solid #2a2e40;
                        border-radius: 16px;
                        padding: 40px;
                        text-align: center;
                        max-width: 440px;
                        box-shadow: 0 12px 32px rgba(0,0,0,0.4);
                    }
                    .icon-box {
                        width: 64px;
                        height: 64px;
                        border-radius: 50%;
                        background: rgba(0,0,0,0.2);
                        border: 2px solid $accentColor;
                        color: $accentColor;
                        font-size: 32px;
                        line-height: 64px;
                        margin: 0 auto 20px;
                    }
                    h1 {
                        font-size: 22px;
                        margin: 0 0 12px;
                    }
                    p {
                        font-size: 14px;
                        color: $subColor;
                        margin: 0 0 24px;
                        line-height: 1.6;
                    }
                    .brand {
                        font-size: 12px;
                        color: #64748b;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon-box">$icon</div>
                    <h1>$title</h1>
                    <p>$message</p>
                    <div class="brand">Antigravity Studio</div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
