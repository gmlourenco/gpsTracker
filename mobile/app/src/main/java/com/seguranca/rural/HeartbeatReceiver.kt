package com.seguranca.rural

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "HeartbeatReceiver"

/**
 * HeartbeatReceiver — Doze-bypassing alarm receiver for 30-min guaranteed sync.
 *
 * This receiver is triggered exactly every 30 minutes by [LocationForegroundService]
 * using AlarmManager.setExactAndAllowWhileIdle(). It wakes up the CPU even in Deep Doze
 * to force a heartbeat record into the database.
 */
class HeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Heartbeat alarm triggered. Waking CPU from Doze...")

        // goAsync() tells the system we need a little more time to process the broadcast
        // before it puts the device back to sleep.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Send an intent to the foreground service to process the heartbeat.
                // The service holds the wakelock naturally and handles DB writes safely.
                val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
                    action = LocationForegroundService.ACTION_HEARTBEAT
                }
                
                // If the app is in the background on Android 8+, startService will throw 
                // an IllegalStateException unless the service is already running as a 
                // Foreground Service. Since we only schedule the alarm when the foreground 
                // service is running, startService is safe here.
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to trigger heartbeat service: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
