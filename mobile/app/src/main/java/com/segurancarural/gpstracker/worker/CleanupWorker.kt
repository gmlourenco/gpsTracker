package com.segurancarural.gpstracker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.segurancarural.gpstracker.util.localHistoryDays
import com.segurancarural.gpstracker.util.localHistoryMaxGb
import java.util.concurrent.TimeUnit

private const val TAG = "CleanupWorker"
private const val WORK_NAME = "local_history_cleanup"

/**
 * CleanupWorker — WorkManager task that runs once a day to clean up the local 
 * offline telemetry cache based on the user's retention settings.
 * Ensures the database doesn't grow unboundedly when the app is never opened.
 */
class CleanupWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running daily local history memory scoop")
        
        val db = (context.applicationContext as com.segurancarural.gpstracker.GpsTrackerApplication).database
        val dao = db.telemetryDao()
        
        val prefsDays = context.localHistoryDays()
        val prefsMaxGb = context.localHistoryMaxGb()

        try {
            val cutoffMs = System.currentTimeMillis() - (prefsDays * 24L * 60 * 60 * 1000)
            val deleted = dao.deleteOlderThan(cutoffMs)
            Log.d(TAG, "Deleted $deleted old records exceeding $prefsDays days")

            val maxBytes = (prefsMaxGb * 1024 * 1024 * 1024).toLong()
            val dbFile = context.getDatabasePath(com.segurancarural.gpstracker.data.db.AppDatabase.DATABASE_NAME)
            if (dbFile.exists() && dbFile.length() > maxBytes) {
                Log.w(TAG, "DB size (${dbFile.length()} bytes) exceeds max size ($maxBytes bytes). Triggering failsafe cleanup.")
                val aggressiveCutoffMs = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
                val aggressiveDeleted = dao.deleteOlderThan(aggressiveCutoffMs)
                Log.w(TAG, "Failsafe deleted $aggressiveDeleted records older than 3 days")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup local history: ${e.message}", e)
            return Result.retry()
        }
        
        return Result.success()
    }

    companion object {
        /** Schedule the daily cleanup worker. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "Cleanup worker scheduled (daily)")
        }
    }
}
