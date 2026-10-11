package com.cashmemer.ui.language

import androidx.annotation.StringRes
import com.cashmemer.R

/**
 * Every language the app supports. To add a language: add one entry here and a values-xx folder
 * with its strings. The globe menu reads this list, so the new language appears automatically.
 */
enum class AppLanguage(
    val tag: String,
    val menuLabel: String,
    val shortLabel: String,
    @StringRes val nameRes: Int,
) {
    ENGLISH("en", "English (ENG)", "ENG", R.string.language_name_en),
    URDU("ur", "Urdu (اردو)", "اردو", R.string.language_name_ur),
    CHINESE("zh", "Chinese (中文)", "中文", R.string.language_name_zh),
    ARABIC("ar", "Arabic (العربية)", "العربية", R.string.language_name_ar);

    companion object {
        /** Maps a locale tag such as "zh-CN" to its language. Unknown tags fall back to English. */
        fun fromTag(appliedTag: String): AppLanguage =
            values().firstOrNull { appliedTag.startsWith(it.tag) } ?: ENGLISH
    }
}
