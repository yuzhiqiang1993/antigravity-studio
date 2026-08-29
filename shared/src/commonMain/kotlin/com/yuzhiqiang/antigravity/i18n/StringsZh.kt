package com.yuzhiqiang.antigravity.i18n

import com.yuzhiqiang.antigravity.i18n.sections.ActivityStrings
import com.yuzhiqiang.antigravity.i18n.sections.ActivityStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.AdvancedStrings
import com.yuzhiqiang.antigravity.i18n.sections.AdvancedStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.CoreStrings
import com.yuzhiqiang.antigravity.i18n.sections.CoreStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.DoctorStrings
import com.yuzhiqiang.antigravity.i18n.sections.DoctorStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.ModelsStrings
import com.yuzhiqiang.antigravity.i18n.sections.ModelsStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.ProviderStrings
import com.yuzhiqiang.antigravity.i18n.sections.ProviderStringsZh
import com.yuzhiqiang.antigravity.i18n.sections.SettingsStrings
import com.yuzhiqiang.antigravity.i18n.sections.SettingsStringsZh

object StringsZh : Strings,
    CoreStrings by CoreStringsZh,
    ModelsStrings by ModelsStringsZh,
    ProviderStrings by ProviderStringsZh,
    ActivityStrings by ActivityStringsZh,
    SettingsStrings by SettingsStringsZh,
    DoctorStrings by DoctorStringsZh,
    AdvancedStrings by AdvancedStringsZh
