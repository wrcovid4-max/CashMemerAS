package com.cashmemer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.cashmemer.core.ui.theme.Holiday

/**
 * Shows the Halloween launcher icon when Holiday.HALLOWEEN is on, the normal icon otherwise.
 * The launcher picks up the change after a short while.
 */
object LauncherIcon {
    fun apply(context: Context) {
        val pm = context.packageManager
        val base = MainActivity::class.java.name.substringBeforeLast('.')
        val halloween = ComponentName(context.packageName, "$base.LauncherHalloween")
        val normal = ComponentName(context.packageName, "$base.LauncherNormal")
        pm.setComponentEnabledSetting(
            halloween,
            if (Holiday.HALLOWEEN) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            normal,
            if (Holiday.HALLOWEEN) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
