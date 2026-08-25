package com.yuzhiqiang.antigravity.domain.model.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 账号订阅等级与状态
 */
@Serializable
enum class AccountTier {
    @SerialName("FREE")
    FREE,

    @SerialName("PRO")
    PRO,

    @SerialName("ULTRA")
    ULTRA,

    @SerialName("ENTERPRISE")
    ENTERPRISE
}

@Serializable
enum class AccountStatus {
    @SerialName("ACTIVE")
    ACTIVE,

    @SerialName("STANDBY")
    STANDBY,

    @SerialName("EXPIRED")
    EXPIRED,

    @SerialName("ERROR")
    ERROR
}

/**
 * 账号用户档案信息
 */
@Serializable
data class AccountProfile(
    val email: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val tier: AccountTier = AccountTier.FREE,
    val planName: String? = null
)
