package com.segurancarural.gpstracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GpsTrackerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                "🚨 Alertas SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de emergência SOS de membros da família"
                enableVibration(true)
            }

            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "Notificações GPS Tracker",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações gerais do GPS Tracker"
            }

            notificationManager.createNotificationChannel(sosChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    companion object {
        const val SOS_CHANNEL_ID = "sos_push_channel"
        const val GENERAL_CHANNEL_ID = "general_push_channel"
    }
}
