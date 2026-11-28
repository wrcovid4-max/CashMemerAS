package com.cashmemer

import android.app.Application
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.SettingsStore

class CashMemerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.cashmemer.core.ui.theme.AccentPreference.init(this)   // before any screen draws
    }

    val repository: CashMemerRepository by lazy { CashMemerRepository.get(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
