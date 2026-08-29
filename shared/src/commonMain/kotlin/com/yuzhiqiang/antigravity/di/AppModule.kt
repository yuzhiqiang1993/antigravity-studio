package com.yuzhiqiang.antigravity.di

import com.yuzhiqiang.antigravity.data.storage.AccountStore
import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.services.auth.GoogleAuthService
import com.yuzhiqiang.antigravity.services.quota.QuotaFetchService
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ConfigStore() }
    single { AccountStore() }
    single { GoogleAuthService() }
    single {
        val googleAuthService = get<GoogleAuthService>()
        QuotaFetchService(
            tokenRefreshCallback = { refreshToken ->
                googleAuthService.refreshAccessToken(refreshToken).map { it.accessToken }
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
            quotaFetchService = get()
        )
    }
}
