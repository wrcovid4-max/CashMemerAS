package com.cashmemer.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.cashmemer.core.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BrandGreenContainer,
    onPrimaryContainer = OnBrandGreenContainer,
    secondary = BrandGreenLight,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = BrandGreenContainer,
    onSecondaryContainer = OnBrandGreenContainer,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = PaperOutline,
    error = DangerRed,
    errorContainer = DangerContainer,
    onErrorContainer = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = DarkGreen,
    onPrimary = BrandGreenDark,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = BrandGreenContainer,
    secondary = DarkGreen,
    onSecondary = BrandGreenDark,
    secondaryContainer = DarkGreenContainer,
    onSecondaryContainer = BrandGreenContainer,
    background = DarkBackground,
    onBackground = PaperBackground,
    surface = DarkSurface,
    onSurface = PaperBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = PaperOutline,
    outline = DarkOutline,
    error = DangerLight,
    errorContainer = DarkSurfaceVariant,
    onErrorContainer = DangerLight,
)

@Composable
fun CashMemerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = CashMemerTypography,
        content = content,
    )
}
