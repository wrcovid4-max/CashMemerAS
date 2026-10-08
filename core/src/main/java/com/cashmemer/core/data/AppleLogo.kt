package com.cashmemer.core.data

/**
 * The Apple logo is a private-use character (U+F8FF) that only Apple's own fonts draw.
 * Android and web browsers show it as an empty box. Text is converted to the standard
 * apple emoji (U+1F34E, red apple), which both platforms draw, as it is entered.
 */
object AppleLogo {
    private const val APPLE_LOGO = "\uF8FF"
    const val APPLE_EMOJI = "\uD83C\uDF4E"

    fun normalize(text: String): String =
        if (text.contains(APPLE_LOGO)) text.replace(APPLE_LOGO, APPLE_EMOJI) else text
}
