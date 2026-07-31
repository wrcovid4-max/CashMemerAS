package com.cashmemer.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Runs one snapshot on the schedule set by [BackupScheduler]. */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return BackupWriter.run(applicationContext).fold(
            onSuccess = { Result.success() },
            // A revoked folder permission won't fix itself on retry — don't spin.
            onFailure = { if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry() },
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

object BackupScheduler {

    private const val WORK_NAME = "cashmemer_auto_backup"

    /** Daily snapshot, only while charging is not required — shops close early. */
    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
