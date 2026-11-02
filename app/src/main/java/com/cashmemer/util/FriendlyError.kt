package com.cashmemer.util

import android.content.Context
import androidx.annotation.StringRes
import com.cashmemer.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns an exception into a message a shop owner can act on. The raw text of an exception
 * ("Unable to resolve host ...") is never shown: no connection gets a plain "check your internet"
 * line, and everything else gets the screen's own short "couldn't do that" message.
 */
object FriendlyError {
    fun isNetwork(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth < 8) {
            if (cause is UnknownHostException || cause is ConnectException || cause is SocketTimeoutException ||
                cause is NoRouteToHostException || cause is SSLException
            ) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    fun message(context: Context, error: Throwable?, @StringRes fallback: Int = R.string.err_couldnt_load): String =
        context.getString(if (isNetwork(error)) R.string.err_no_internet else fallback)
}
