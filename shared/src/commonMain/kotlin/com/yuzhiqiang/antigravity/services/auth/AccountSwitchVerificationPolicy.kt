package com.yuzhiqiang.antigravity.services.auth

/**
 * 同一宿主在运行态与静态配置面上的独立账号观测。
 */
internal data class AccountObservations(
    val runtimeEmail: String? = null,
    val configuredEmail: String? = null
)

/**
 * 将账号观测归约为切号状态。该策略不执行 IO，便于覆盖完整状态矩阵。
 */
internal object AccountSwitchVerificationPolicy {

    fun ideResult(
        requested: Boolean,
        targetEmail: String,
        observations: AccountObservations,
        wasRunning: Boolean,
        restartRequested: Boolean,
        isRunning: Boolean,
        isUnavailable: Boolean
    ): HotSwitchCoordinator.TargetResult {
        if (!requested) {
            return result(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }

        val actualEmail = observations.runtimeEmail ?: observations.configuredEmail
        if (matches(observations.runtimeEmail, targetEmail)) {
            return result(
                HotSwitchCoordinator.TargetStatus.CONFIRMED,
                actualEmail,
                "IDE 运行态已确认目标账号"
            )
        }
        if (isUnavailable) {
            return result(
                HotSwitchCoordinator.TargetStatus.NOT_AVAILABLE,
                actualEmail,
                "IDE 未安装或尚未初始化账号数据库"
            )
        }

        val configured = matches(observations.configuredEmail, targetEmail)
        if (wasRunning && !restartRequested) {
            return if (configured) {
                result(
                    HotSwitchCoordinator.TargetStatus.PENDING_RESTART,
                    actualEmail,
                    "IDE 账号数据库已配置，重启后加载目标账号"
                )
            } else {
                result(
                    HotSwitchCoordinator.TargetStatus.FAILED,
                    actualEmail,
                    "IDE 正在运行且账号数据库未写入目标账号"
                )
            }
        }
        if (restartRequested && !isRunning) {
            return result(
                HotSwitchCoordinator.TargetStatus.FAILED,
                actualEmail,
                "IDE 未进入或未保持运行状态"
            )
        }
        if (observations.runtimeEmail != null) {
            return result(
                HotSwitchCoordinator.TargetStatus.FAILED,
                observations.runtimeEmail,
                "IDE 运行态仍为其他账号"
            )
        }
        if (configured) {
            return result(
                HotSwitchCoordinator.TargetStatus.CONFIGURED,
                observations.configuredEmail,
                "IDE 账号数据库已配置，运行态尚未确认"
            )
        }
        return result(
            HotSwitchCoordinator.TargetStatus.FAILED,
            actualEmail,
            "IDE 未确认目标账号"
        )
    }

    fun appCliResult(
        requested: Boolean,
        targetEmail: String,
        observations: AccountObservations,
        credentialsMatchTarget: Boolean,
        wasRunning: Boolean,
        restartRequested: Boolean,
        isRunning: Boolean,
        isUnavailable: Boolean
    ): HotSwitchCoordinator.TargetResult {
        if (!requested) {
            return result(HotSwitchCoordinator.TargetStatus.NOT_REQUESTED)
        }

        val actualEmail = observations.runtimeEmail ?: observations.configuredEmail
        if (!credentialsMatchTarget) {
            return result(
                HotSwitchCoordinator.TargetStatus.FAILED,
                actualEmail,
                "App & CLI 共享凭据文件未确认目标账号"
            )
        }
        if (matches(observations.runtimeEmail, targetEmail)) {
            return result(
                HotSwitchCoordinator.TargetStatus.CONFIRMED,
                observations.runtimeEmail,
                "App & CLI 共享凭据已写入，App 运行态已确认"
            )
        }
        if (wasRunning && !restartRequested) {
            return result(
                HotSwitchCoordinator.TargetStatus.PENDING_RESTART,
                actualEmail,
                "App & CLI 共享凭据已写入，App 重启后加载目标账号"
            )
        }
        if (isUnavailable) {
            return result(
                HotSwitchCoordinator.TargetStatus.CONFIGURED,
                observations.configuredEmail,
                "CLI 共享凭据已配置；未安装 App，无法进行运行态确认"
            )
        }
        if (restartRequested && !isRunning) {
            return result(
                HotSwitchCoordinator.TargetStatus.FAILED,
                actualEmail,
                "App & CLI 共享凭据已写入，但 App 未能保持运行"
            )
        }
        if (observations.runtimeEmail != null) {
            return result(
                HotSwitchCoordinator.TargetStatus.FAILED,
                observations.runtimeEmail,
                "App 运行态仍为其他账号"
            )
        }
        return result(
            HotSwitchCoordinator.TargetStatus.CONFIGURED,
            observations.configuredEmail,
            "App & CLI 共享凭据已配置，App 运行态尚未确认"
        )
    }

    private fun result(
        status: HotSwitchCoordinator.TargetStatus,
        actualEmail: String? = null,
        message: String? = null
    ): HotSwitchCoordinator.TargetResult {
        return HotSwitchCoordinator.TargetResult(status, actualEmail, message)
    }

    private fun matches(actualEmail: String?, targetEmail: String): Boolean {
        return actualEmail?.equals(targetEmail, ignoreCase = true) == true
    }
}
