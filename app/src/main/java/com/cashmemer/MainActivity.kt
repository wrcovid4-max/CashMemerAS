package com.cashmemer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.ui.theme.CashMemerTheme
import com.cashmemer.lock.AppLockGate
import com.cashmemer.ui.CashMemerApp
import com.cashmemer.ui.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsFlow = (application as CashMemerApplication).settingsStore.settings

        setContent {
            val settings by settingsFlow.collectAsState(initial = AppSettings())
            CashMemerTheme(themeMode = settings.themeMode) {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    showSplash = false
                }

                if (showSplash) {
                    SplashScreen()
                } else {
                    AppLockGate(settings = settings) {
                        CashMemerApp(settings = settings)
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MILLIS = 1600L
    }
}
