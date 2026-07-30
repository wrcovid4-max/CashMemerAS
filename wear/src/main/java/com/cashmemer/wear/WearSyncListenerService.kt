package com.cashmemer.wear

import com.cashmemer.core.wear.WearSync
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives summary pushes from the phone and caches them for the watch UI. */
class WearSyncListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearSync.PATH_SUMMARY) return@forEach

            val payload = DataMapItem.fromDataItem(event.dataItem)
                .dataMap
                .getString(WearSync.KEY_PAYLOAD)
                ?: return@forEach

            scope.launch { WearSummaryStore.save(applicationContext, payload) }
        }
    }
}
