package com.segurancarural.gpstracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.segurancarural.gpstracker.data.repository.FcmTokenRepository
import com.segurancarural.gpstracker.ui.activities.MainActivity
import com.segurancarural.gpstracker.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service.
 *
 * Handles two responsibilities:
 *  1. [onNewToken] — called when FCM generates/rotates the device push token.
 *     We immediately upload the new token to our backend so the server can address this device.
 *  2. [onMessageReceived] — called when a push arrives while the app is in the foreground.
 *     When the app is in the background/killed, FCM displays the notification automatically
 *     using the `notification` payload sent by the server — no code needed for that case.
 */
class FcmService : FirebaseMessagingService() {

    companion object {
        const val SOS_CHANNEL_ID    = "sos_push_channel"
        const val GENERAL_CHANNEL_ID = "general_push_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Token refresh ─────────────────────────────────────────────────────────

    /**
     * Called when FCM has a new registration token for this device.
     * Upload immediately so the backend can reach us for future SOS alerts.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppLog.i("FcmService", "FCM token refreshed — uploading to backend")
        serviceScope.launch {
            FcmTokenRepository(applicationContext).uploadToken(token)
        }
    }

    // ── Message received (foreground) ─────────────────────────────────────────

    /**
     * Called when the app is in the foreground and a data/notification message arrives.
     * For background/killed state, FCM handles display automatically via the `notification` block.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "Alerta"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val isSos = message.data["type"] == "SOS"

        AppLog.i("FcmService", "Push received — title=$title sos=$isSos")

        showNotification(
            channelId   = if (isSos) SOS_CHANNEL_ID else GENERAL_CHANNEL_ID,
            title       = title,
            body        = body,
            isSos       = isSos,
            data        = message.data,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showNotification(
        channelId: String,
        title: String,
        body: String,
        isSos: Boolean,
        data: Map<String, String> = emptyMap(),
    ) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists (safe to call repeatedly)
        ensureChannel(notificationManager, channelId, isSos)

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("click_action", "OPEN_SOS")
                putExtra("lat", data["lat"])
                putExtra("lng", data["lng"])
                putExtra("deviceLabel", data["deviceLabel"])
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(if (isSos) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .apply { if (isSos) setVibrate(longArrayOf(0, 500, 200, 500, 200, 500)) }
            .build()

        notificationManager.notify(if (isSos) 911 else System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel(manager: NotificationManager, channelId: String, isSos: Boolean) {
        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            if (isSos) "🚨 Alertas SOS" else "Notificações GPS Tracker",
            if (isSos) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = if (isSos)
                "Notificações de emergência SOS de membros da família"
            else
                "Notificações gerais do GPS Tracker"
            if (isSos) enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
