package com.yuzhiqiang.antigravity.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val displayName: String) {
    ZH_CN("zh-CN", "简体中文"),
    EN_US("en-US", "English");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ZH_CN
    }
}

object I18nManager {
    var currentLanguage: AppLanguage by mutableStateOf(AppLanguage.ZH_CN)

    val strings: Strings
        get() = if (currentLanguage == AppLanguage.ZH_CN) StringsZh else StringsEn
}

val LocalStrings = compositionLocalOf<Strings> { StringsZh }

@Composable
fun strings(): Strings = LocalStrings.current
