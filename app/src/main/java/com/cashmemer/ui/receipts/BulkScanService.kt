package com.cashmemer.ui.receipts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cashmemer.R
import com.cashmemer.core.network.GeminiOcrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Long edge, in pixels, that photos are shrunk to before upload. */
private const val SERVICE_MAX_EDGE = 1600

/**
 * Scans queued receipt photos in the background as a foreground service, so scanning
 * carries on when the app is in the background. Progress and time left show in the
 * notification shade.
 */
class BulkScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var working = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (done, total) = BulkScanSession.progressCounts()
        ServiceCompat.startForeground(
            this,
            BulkNotifier.ID,
            BulkNotifier.progress(this, done, total, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val start = synchronized(lock) {
            if (working) false else {
                working = true
                true
            }
        }
        if (start) scope.launch { processQueue() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private suspend fun processQueue() {
        while (true) {
            val next = BulkScanSession.takeNext()
            if (next != null) {
                scanOne(next.first, next.second)
                continue
            }
            val finished = synchronized(lock) {
                if (BulkScanSession.hasPending()) false else {
                    working = false
                    true
                }
            }
            if (finished) break
        }
        val (done, total) = BulkScanSession.progressCounts()
        BulkNotifier.post(this, BulkNotifier.finished(this, done, total))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private suspend fun scanOne(id: Int, uri: Uri) {
        val started = System.currentTimeMillis()
        BulkScanSession.update(id) { it.copy(status = BulkStatus.Scanning) }
        val bitmap = decodeScaled(uri)
        val result = bitmap?.let { GeminiOcrClient.parse(it).getOrNull() }
        BulkScanSession.update(id) {
            it.copy(
                status = if (result != null) BulkStatus.Done else BulkStatus.Failed,
                parsed = result,
            )
        }
        BulkScanSession.recordDuration(System.currentTimeMillis() - started)
        val (done, total) = BulkScanSession.progressCounts()
        val eta = BulkScanSession.etaMillis(total - done)
        BulkNotifier.post(this, BulkNotifier.progress(this, done, total, eta))
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > SERVICE_MAX_EDGE) {
                val scale = SERVICE_MAX_EDGE.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }.getOrNull()
}

/** Builds and posts the bulk scan notifications. */
object BulkNotifier {
    const val ID = 4101
    private const val CHANNEL = "bulk_scan"

    fun progress(context: Context, done: Int, total: Int, eta: Long?): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.bulk_notif_title))
            .setContentText(
                context.getString(R.string.bulk_progress, done, total) + " · " + formatEta(context, eta)
            )
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun finished(context: Context, done: Int, total: Int): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(context.getString(R.string.bulk_notif_done))
            .setContentText(context.getString(R.string.bulk_progress, done, total))
            .setAutoCancel(true)
            .build()
    }

    /** Posts the notification if the user allows notifications; the scan runs either way. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, notification: Notification) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.bulk_notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/** Time-left estimate as a short phrase, for the notification. */
private fun formatEta(context: Context, millis: Long?): String = when {
    millis == null -> context.getString(R.string.bulk_eta_calculating)
    millis >= 60_000 -> context.getString(R.string.bulk_eta_minutes, (millis / 60_000).toInt() + 1)
    else -> context.getString(R.string.bulk_eta_seconds, maxOf(1, (millis / 1000).toInt()))
}
