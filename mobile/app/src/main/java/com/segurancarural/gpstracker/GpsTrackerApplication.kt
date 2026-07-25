package com.segurancarural.gpstracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.segurancarural.gpstracker.data.db.AppDatabase
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.di.AndroidPlatformDependencies
import com.segurancarural.gpstracker.di.appModule
import io.github.jan.supabase.compose.auth.googleNativeLogin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GpsTrackerApplication : Application() {

    val database: AppDatabase by lazy {
        createAppDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        com.segurancarural.gpstracker.util.OfflineLogger.init(this)
        createNotificationChannels()
        Platform.dependencies = AndroidPlatformDependencies(this)

        com.segurancarural.gpstracker.data.network.SharedConfig.SUPABASE_URL = BuildConfig.SUPABASE_URL
        com.segurancarural.gpstracker.data.network.SharedConfig.SUPABASE_KEY = BuildConfig.SUPABASE_KEY
        com.segurancarural.gpstracker.data.network.SharedConfig.DEVICE_API_SECRET = BuildConfig.DEVICE_API_SECRET
        com.segurancarural.gpstracker.data.network.SharedConfig.BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
        com.segurancarural.gpstracker.data.network.SharedConfig.IS_DEBUG = BuildConfig.DEBUG

        com.segurancarural.gpstracker.data.network.SupabaseClient.init {
            install(io.github.jan.supabase.compose.auth.ComposeAuth) {
                googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
            }
        }

        startKoin {
            androidContext(this@GpsTrackerApplication)
            modules(appModule)
        }
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
