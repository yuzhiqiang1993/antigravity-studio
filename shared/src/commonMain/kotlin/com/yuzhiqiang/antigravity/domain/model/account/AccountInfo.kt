package com.yuzhiqiang.antigravity.domain.model.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 完整账号聚合模型
 */
@Serializable
data class AccountInfo(
    val id: String,
    val profile: AccountProfile,
    val tokens: OAuthTokens,
    @SerialName("is_active")
    val isActive: Boolean = false,
    @SerialName("is_pinned")
    val isPinned: Boolean = false,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
    @SerialName("status")
    val status: AccountStatus = AccountStatus.STANDBY,
    @SerialName("added_at")
    val addedAt: Long = System.currentTimeMillis(),
    @SerialName("last_refreshed_at")
    val lastRefreshedAt: Long = System.currentTimeMillis(),
    @SerialName("last_error_message")
    val lastErrorMessage: String? = null,
    @SerialName("custom_note")
    val customNote: String? = null
) {
    val email: String
        get() = profile.email

    val displayName: String
        get() = customNote?.takeIf { it.isNotBlank() }
            ?: profile.name?.takeIf { it.isNotBlank() }
            ?: email

    fun maskedEmail(): String {
        val parts = email.split('@')
        if (parts.size != 2) return email
        val local = parts[0]
        val domain = parts[1]
        val visibleLength = (local.length / 2).coerceIn(2, 4)
        return local.take(visibleLength) + "****@" + domain
    }
}

