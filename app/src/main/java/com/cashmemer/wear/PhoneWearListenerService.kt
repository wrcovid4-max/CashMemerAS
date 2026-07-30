package com.cashmemer.wear

import com.cashmemer.core.wear.WearSync
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Answers the watch's "send me fresh numbers" message. */
class PhoneWearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearSync.PATH_REQUEST_REFRESH) return
        scope.launch { PhoneWearSyncManager.push(applicationContext) }
    }
}
