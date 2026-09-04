package com.yuzhiqiang.antigravity.di

import com.yuzhiqiang.antigravity.core.concurrency.AppDispatchers
import com.yuzhiqiang.antigravity.core.concurrency.DefaultAppDispatchers
import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.data.usage.UsageRepository
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.services.quota.QuotaFetchService
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
    single { ConfigStore() }
    single { AccountStore() }
    single { UsageRepository() }
    single { GoogleAuthService() }
    single {
        val googleAuthService = get<GoogleAuthService>()
        val accountStore = get<AccountStore>()
        QuotaFetchService(
            tokenRefreshCallback = { refreshToken ->
                val result = googleAuthService.refreshAccessToken(refreshToken)
                if (result.isSuccess) {
                    val newTokens = result.getOrThrow()
                    val targetAccount = accountStore.accountsState.value.firstOrNull {
                        it.tokens.refreshToken == refreshToken
                    }
                    if (targetAccount != null) {
                        accountStore.updateTokens(targetAccount.email, newTokens)
                    }
                    Result.success(newTokens.accessToken)
                } else {
                    Result.failure(result.exceptionOrNull() ?: IllegalStateException("Token 刷新失败"))
                }
            }
        )
    }
    single { LocalProxyServer(get()) }
    single { DoctorEngine(get(), get()) }
    viewModel {
        AppViewModel(
            configStore = get(),
            proxyServer = get(),
            doctorEngine = get(),
            accountStore = get(),
            googleAuthService = get(),
            quotaFetchService = get(),
            usageRepository = get()
        )
    }
}
