package com.cashmemer.core.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.cashmemer.core.data.AccentTheme

/**
 * The colour the whole app is drawn in. It is a Compose state, so changing it repaints every screen at once, including
 * the ones that open as separate activities. A copy is kept in SharedPreferences so the right colour is known before the
 * settings database has loaded (no green flash at launch for someone on blue).
 */
object AccentPreference {
    private const val PREFS = "cashmemer_theme"
    private const val KEY = "accent"

    var current by mutableStateOf(AccentTheme.GREEN)
        private set

    fun init(context: Context) {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = AccentTheme.entries.firstOrNull { it.name == raw } ?: AccentTheme.GREEN
    }

    fun set(context: Context, accent: AccentTheme) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, accent.name).apply()
        current = accent
    }
}

/** The colour shown on the picker swatch. */
fun AccentTheme.swatch(): Color = when (this) {
    AccentTheme.GREEN -> Color(0xFF12D63B)
    AccentTheme.BLUE -> Color(0xFF1F6FEB)
    AccentTheme.RED -> Color(0xFFC62828)
    AccentTheme.YELLOW -> Color(0xFFF2B600)
}

/** Colour for a tick drawn on top of the swatch, chosen for contrast. */
fun AccentTheme.onSwatch(): Color = when (this) {
    AccentTheme.GREEN, AccentTheme.YELLOW -> Color(0xFF11150C)
    AccentTheme.BLUE, AccentTheme.RED -> Color.White
}
