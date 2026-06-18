package com.segurancarural.gpstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.segurancarural.gpstracker.service.LocationForegroundService

/**
 * AccidentReceiver - Receives broadcasted click events from the accident countdown notification
 * (Cancel or Trigger Immediately) and relays them to the LocationForegroundService.
 */
class AccidentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AccidentReceiver"
        const val ACTION_ACCIDENT_CANCEL = "com.segurancarural.gpstracker.action.ACCIDENT_CANCEL"
        const val ACTION_ACCIDENT_TRIGGER = "com.segurancarural.gpstracker.action.ACCIDENT_TRIGGER"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "Accident action received: $action")

        val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
            this.action = when (action) {
                ACTION_ACCIDENT_CANCEL -> LocationForegroundService.ACTION_ACCIDENT_CANCEL
                ACTION_ACCIDENT_TRIGGER -> LocationForegroundService.ACTION_ACCIDENT_TRIGGER
                else -> null
            }
        }

        if (serviceIntent.action != null) {
            // Start the service to handle the action
            context.startService(serviceIntent)
        }
    }
}
