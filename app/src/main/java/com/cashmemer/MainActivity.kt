package com.cashmemer

import com.cashmemer.ui.effects.AutumnLeaves
import com.cashmemer.ui.effects.HalloweenDecor
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import com.cashmemer.audio.AppSounds
import com.cashmemer.ui.receipts.BarcodeScanBus
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import android.app.Activity
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
        handleBarcodeIntent(intent)

        val settingsFlow = (application as CashMemerApplication).settingsStore.settings

        setContent {
            val settings by settingsFlow.collectAsState(initial = AppSettings())
            LaunchedEffect(settings.appSounds) { com.cashmemer.audio.AppSounds.enabled = settings.appSounds }
            CashMemerTheme(themeMode = settings.themeMode) {
                SystemBarIcons()
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_MILLIS)
                    showSplash = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        AppLockGate(settings = settings) {
                            CashMemerApp(settings = settings)
                        }
                    }
                    AutumnLeaves()
                    HalloweenDecor()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleBarcodeIntent(intent)
    }

    private fun handleBarcodeIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(BarcodeScanBus.EXTRA_OPEN_BARCODE, false) == true) {
            BarcodeScanBus.request()
        }
    }

    private companion object {
        const val SPLASH_MILLIS = 1600L
    }
}

/**
 * Dark status bar icons on light backgrounds, light icons on dark ones. Without this,
 * Android keeps the system default, which is white icons on a light app.
 */
@Composable
private fun SystemBarIcons() {
    val view = LocalView.current
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
