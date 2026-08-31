package com.yuzhiqiang.antigravity.i18n

import com.yuzhiqiang.antigravity.i18n.sections.ActivityStrings
import com.yuzhiqiang.antigravity.i18n.sections.ActivityStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.AdvancedStrings
import com.yuzhiqiang.antigravity.i18n.sections.AdvancedStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.CoreStrings
import com.yuzhiqiang.antigravity.i18n.sections.CoreStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.DoctorStrings
import com.yuzhiqiang.antigravity.i18n.sections.DoctorStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.ModelsStrings
import com.yuzhiqiang.antigravity.i18n.sections.ModelsStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.ProviderStrings
import com.yuzhiqiang.antigravity.i18n.sections.ProviderStringsEn
import com.yuzhiqiang.antigravity.i18n.sections.SettingsStrings
import com.yuzhiqiang.antigravity.i18n.sections.SettingsStringsEn

import com.yuzhiqiang.antigravity.i18n.sections.UsageStrings
import com.yuzhiqiang.antigravity.i18n.sections.UsageStringsEn

object StringsEn : Strings,
    CoreStrings by CoreStringsEn,
    ModelsStrings by ModelsStringsEn,
    ProviderStrings by ProviderStringsEn,
    ActivityStrings by ActivityStringsEn,
    SettingsStrings by SettingsStringsEn,
    DoctorStrings by DoctorStringsEn,
    AdvancedStrings by AdvancedStringsEn,
    UsageStrings by UsageStringsEn
