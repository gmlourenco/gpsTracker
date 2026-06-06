package com.segurancarural.gpstracker.worker

import com.segurancarural.gpstracker.BuildConfig
import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.sync.SyncEngine
import com.segurancarural.gpstracker.data.repository.OfflineRequestManager
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.util.shouldUploadOverCurrentNetwork
import java.util.concurrent.TimeUnit

private const val TAG = "SyncWorker"
private const val WORK_NAME = "telemetry_sync"

/**
 * SyncWorker — WorkManager task that flushes the offline telemetry queue
 * to the backend whenever the device has a network connection.
 *
 * Scheduling:
 *   - Periodic: every 15 minutes, only when network is available
 *   - Constraints: [NetworkType.CONNECTED]
 *   - Back-off: exponential, starting at 30 seconds
 *
 * The worker delegates all sync logic to [SyncEngine] which applies the
 * 3-phase asymmetric flush (SOS LIFO → Latest Point → FIFO history).
 *
 * Schedule via [SyncWorker.schedule] from MainActivity or after boot.
 */
class SyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Sync work started")

        if (!shouldUploadOverCurrentNetwork(context)) {
            Log.d(TAG, "Mobile data sync disabled — deferring flush until Wi‑Fi")
            return Result.success()
        }

        // Try syncing any pending offline requests first
        try {
            OfflineRequestManager.processQueue(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process offline queue: ${e.message}", e)
        }

        val db = createAppDatabase(context)
        val unsyncedCount = db.telemetryDao().getUnsyncedCount()

        if (unsyncedCount == 0) {
            Log.d(TAG, "Queue is empty — nothing to sync")
            return Result.success()
        }

        Log.i(TAG, "Syncing $unsyncedCount unsynced records")

        val httpClient = ApiClient.httpClient

        return try {
            val engine = SyncEngine(
                dao = db.telemetryDao(),
                httpClient = httpClient,
                backendBaseUrl = BuildConfig.BACKEND_BASE_URL,
            )

            val result = engine.flush()

            if (result.hasErrors) {
                Log.w(TAG, "Sync completed with ${result.errors} errors — synced ${result.totalSynced}")
                Result.retry()  // WorkManager will retry with back-off
            } else {
                Log.i(TAG, "Sync successful — synced ${result.totalSynced} records")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {

        /**
         * Schedule the periodic sync worker. Safe to call multiple times —
         * [ExistingPeriodicWorkPolicy.KEEP] ensures only one instance runs.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Sync worker scheduled (every 15 min, network required)")
        }

        /** Cancel the periodic sync worker. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Sync worker cancelled")
        }
    }
}
