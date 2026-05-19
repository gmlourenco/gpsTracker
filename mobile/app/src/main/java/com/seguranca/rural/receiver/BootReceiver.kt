package com.seguranca.rural.receiver

import com.seguranca.rural.service.LocationForegroundService
import com.seguranca.rural.worker.SyncWorker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

private const val TAG = "BootReceiver"

/**
 * BootReceiver — auto-restarts GPS tracking after device reboot.
 *
 * Listens for both [Intent.ACTION_BOOT_COMPLETED] (standard boot) and
 * [Intent.ACTION_LOCKED_BOOT_COMPLETED] (Direct Boot — fires before decryption
 * on devices with lock screen). The standard boot is sufficient for most cases;
 * LOCKED_BOOT is a belt-and-suspenders addition for critical safety devices.
 *
 * Only starts the [LocationForegroundService] if the user had tracking enabled
 * before the reboot (persisted in SharedPreferences as `tracking_active = true`).
 * This respects the user's intent — if they stopped tracking before rebooting,
 * we don't silently re-enable it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We intentionally only handle BOOT_COMPLETED (not LOCKED_BOOT_COMPLETED).
        // LOCKED_BOOT_COMPLETED fires before credential-encrypted storage is unlocked,
        // so SharedPreferences ("tracking_active") would always return false anyway.
        // This also prevents the duplicate service start we saw in Logcat.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring boot action: ${intent.action}")
            return
        }

        Log.i(TAG, "Boot received — checking tracking preference")

        val prefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        val isTrackingEnabled = prefs.getBoolean("tracking_active", false)

        if (!isTrackingEnabled) {
            Log.i(TAG, "Tracking was disabled before reboot — not auto-starting")
            return
        }

        Log.i(TAG, "Tracking was active before reboot — restarting LocationForegroundService")

        val serviceIntent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        SyncWorker.schedule(context)
    }
}
