package com.cashmemer.ui.receipts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets History hand a receipt to the form on the Receipts tab.
 *
 * The two screens have separate ViewModels and there is no navigation argument
 * between tabs, so a tiny shared channel is simpler than threading state
 * through the tab host.
 */
object ReceiptEditBus {

    private val _requestedId = MutableStateFlow<Long?>(null)
    val requestedId: StateFlow<Long?> = _requestedId.asStateFlow()

    fun requestEdit(id: Long) {
        _requestedId.value = id
    }

    /** Called once the form has loaded the receipt, so it does not reload. */
    fun consume() {
        _requestedId.value = null
    }
}
