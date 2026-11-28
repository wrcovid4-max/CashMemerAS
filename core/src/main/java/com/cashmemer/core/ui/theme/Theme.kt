package com.cashmemer.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cashmemer.core.data.AccentTheme
import com.cashmemer.core.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    // Near-black, not white. White text on neon green fails contrast badly and
    // reads as a smudge; dark text on a bright fill is what makes neon legible.
    onPrimary = OnBrandGreenContainer,
    primaryContainer = BrandGreenContainer,
    onPrimaryContainer = OnBrandGreenContainer,
    secondary = BrandGreenLight,
    onSecondary = OnBrandGreenContainer,
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

/** Builds a light scheme for the blue, red and yellow themes. Green keeps the original hand-tuned [LightColors]. */
private fun lightAccent(
    primary: Long, onPrimary: Long, container: Long, onContainer: Long, secondary: Long,
    bg: Long, variant: Long, outline: Long, ink: Long, inkSecondary: Long,
): ColorScheme = lightColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
    secondary = Color(secondary), onSecondary = Color(onContainer), secondaryContainer = Color(container), onSecondaryContainer = Color(onContainer),
    background = Color(bg), onBackground = Color(ink), surface = Color.White, onSurface = Color(ink),
    surfaceVariant = Color(variant), onSurfaceVariant = Color(inkSecondary), outline = Color(outline),
    error = DangerRed, errorContainer = DangerContainer, onErrorContainer = DangerRed,
)

private fun darkAccent(
    primary: Long, onPrimary: Long, container: Long, onContainer: Long,
    bg: Long, surface: Long, variant: Long, outline: Long, text: Long, textSecondary: Long,
): ColorScheme = darkColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(container), onPrimaryContainer = Color(onContainer),
    secondary = Color(primary), onSecondary = Color(onPrimary), secondaryContainer = Color(container), onSecondaryContainer = Color(onContainer),
    background = Color(bg), onBackground = Color(text), surface = Color(surface), onSurface = Color(text),
    surfaceVariant = Color(variant), onSurfaceVariant = Color(textSecondary), outline = Color(outline),
    error = DangerLight, errorContainer = Color(variant), onErrorContainer = DangerLight,
)

private val BlueLight = lightAccent(0xFF1F6FEB, 0xFFFFFFFF, 0xFFD6E6FF, 0xFF0A2A5E, 0xFF5B9BFF, 0xFFF4F7FC, 0xFFE6EDF8, 0xFFBFCBE0, 0xFF0F1623, 0xFF44506A)
private val BlueDark = darkAccent(0xFF7DB0FF, 0xFF0A2A5E, 0xFF123A78, 0xFFD6E6FF, 0xFF0E1420, 0xFF161E2E, 0xFF222C42, 0xFF3B4A68, 0xFFF4F7FC, 0xFFBFCBE0)
private val RedLight = lightAccent(0xFFC62828, 0xFFFFFFFF, 0xFFFFD9D6, 0xFF5A0A0A, 0xFFEF6B63, 0xFFFBF5F4, 0xFFF4E4E2, 0xFFE0C4C0, 0xFF1C1110, 0xFF5E4643)
private val RedDark = darkAccent(0xFFFF8A80, 0xFF5A0A0A, 0xFF7A1C1C, 0xFFFFD9D6, 0xFF1A0F0E, 0xFF261716, 0xFF3A2423, 0xFF5E3F3C, 0xFFFBF5F4, 0xFFE0C4C0)
// Yellow is a light colour, so text on it is dark brown rather than white.
private val YellowLight = lightAccent(0xFFF2B600, 0xFF2B2000, 0xFFFFF0B3, 0xFF3B2E00, 0xFFFFD54F, 0xFFFFFBEF, 0xFFF6F0DA, 0xFFDDD2A8, 0xFF1C1807, 0xFF5A5232)
private val YellowDark = darkAccent(0xFFFFD54F, 0xFF3B2E00, 0xFF5C4700, 0xFFFFF0B3, 0xFF17140A, 0xFF221E10, 0xFF332D18, 0xFF584F2E, 0xFFFFFBEF, 0xFFDDD2A8)

@Composable
fun CashMemerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentTheme = AccentPreference.current,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when (accent) {
        AccentTheme.GREEN -> if (dark) DarkColors else LightColors
        AccentTheme.BLUE -> if (dark) BlueDark else BlueLight
        AccentTheme.RED -> if (dark) RedDark else RedLight
        AccentTheme.YELLOW -> if (dark) YellowDark else YellowLight
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = CashMemerTypography,
        content = content,
    )
}
