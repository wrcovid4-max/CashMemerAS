package com.cashmemer.ui.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Launcher shortcuts (long-press the app icon): the Android counterpart of the iOS Shortcuts /
 * Siri actions. `MainActivity` turns the intent action into a request here; the navigation host
 * returns to the Receipts screen and the Receipts tab row switches to the tab asked for.
 */
object ShortcutBus {
    const val ACTION_BARCODE = "com.cashmemer.action.SHORTCUT_BARCODE"
    const val ACTION_HISTORY = "com.cashmemer.action.SHORTCUT_HISTORY"
    const val ACTION_DASHBOARD = "com.cashmemer.action.SHORTCUT_DASHBOARD"

    private val _tab = MutableStateFlow<Int?>(null)

    /** Index of the Receipts tab to show (1 = History, 2 = Dashboard), or null when nothing is waiting. */
    val tab: StateFlow<Int?> = _tab.asStateFlow()

    /** Returns true if [action] was one of ours and has been queued. */
    fun handle(action: String?): Boolean = when (action) {
        ACTION_BARCODE -> { BarcodeScanBus.request(); true }
        ACTION_HISTORY -> { _tab.value = 1; true }
        ACTION_DASHBOARD -> { _tab.value = 2; true }
        else -> false
    }

    fun consume() { _tab.value = null }
}
