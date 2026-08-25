package com.yuzhiqiang.antigravity.domain.model.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 凭证与过期时间戳
 */
@Serializable
data class OAuthTokens(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String,

    /**
     * 过期绝对时间戳 (秒级 Unix Timestamp)
     */
    @SerialName("expiry_timestamp")
    val expiryTimestamp: Long,

    @SerialName("token_type")
    val tokenType: String = "Bearer",

    @SerialName("id_token")
    val idToken: String? = null
) {
    /**
     * 距过期剩余时间（秒）
     */
    fun remainingSeconds(): Long {
        val nowSeconds = System.currentTimeMillis() / 1000L
        return (expiryTimestamp - nowSeconds).coerceAtLeast(0L)
    }

    /**
     * 是否即将过期（默认在过期前 bufferSeconds 秒视为即将过期）
     */
    fun isExpiringSoon(bufferSeconds: Long = 300L): Boolean {
        return remainingSeconds() <= bufferSeconds
    }

    /**
     * 是否已完全过期
     */
    fun isExpired(): Boolean {
        return remainingSeconds() == 0L
    }
}
