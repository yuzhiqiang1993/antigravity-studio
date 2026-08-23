package com.yuzhiqiang.antigravity.di

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.doctor.engine.DoctorEngine
import com.yuzhiqiang.antigravity.proxy.server.LocalProxyServer
import com.yuzhiqiang.antigravity.ui.presentation.AppViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ConfigStore() }
    single { LocalProxyServer(get()) }
    single { DoctorEngine(get(), get()) }
    viewModel { AppViewModel(get(), get(), get()) }
}
