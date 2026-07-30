package com.cashmemer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.ui.theme.CashMemerTheme
import com.cashmemer.ui.CashMemerApp

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsFlow = (application as CashMemerApplication).settingsStore.settings

        setContent {
            val settings by settingsFlow.collectAsState(initial = AppSettings())
            CashMemerTheme(themeMode = settings.themeMode) {
                CashMemerApp(settings = settings)
            }
        }
    }
}
