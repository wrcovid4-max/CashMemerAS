package com.cashmemer.ui.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Asks the app to open the barcode scanner, e.g. when the progress notification is tapped. */
object BarcodeScanBus {
    const val EXTRA_OPEN_BARCODE = "open_barcode"

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    /** Returns true once per request, so the barcode scanner opens only once. */
    fun consume(): Boolean {
        val was = _pending.value
        _pending.value = false
        return was
    }
}
