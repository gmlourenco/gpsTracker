package com.segurancarural.gpstracker.di

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.PlatformDependencies
import com.segurancarural.gpstracker.util.TRACKING_PREFS_NAME
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.deviceMarkerColorArgb
import com.segurancarural.gpstracker.util.ensureSerialNumber

class AndroidPlatformDependencies(private val context: Context) : PlatformDependencies {
    private val prefs = context.getSharedPreferences(TRACKING_PREFS_NAME, Context.MODE_PRIVATE)
    private val offlinePrefs = context.getSharedPreferences("offline_request_prefs", Context.MODE_PRIVATE)

    override fun shouldUploadOverCurrentNetwork(): Boolean {
        return com.segurancarural.gpstracker.util.shouldUploadOverCurrentNetwork(context)
    }

    override fun getDeviceMarkerColorHex(): String? {
        val colorArgb = context.deviceMarkerColorArgb()
        return argbToMapLibreHex(colorArgb)
    }

    override fun ensureSerialNumber(): String {
        return context.ensureSerialNumber()
    }

    override fun getOfflineQueueJson(): String? {
        return offlinePrefs.getString("request_queue", null)
    }

    override fun saveOfflineQueueJson(json: String) {
        offlinePrefs.edit().putString("request_queue", json).apply()
    }

    override fun getFarmId(): String? {
        return prefs.getString("farm_id", null)
    }

    override fun setFarmId(farmId: String?) {
        prefs.edit().putString("farm_id", farmId).apply()
    }

    override fun getSupabaseJwt(): String? {
        return prefs.getString("supabase_jwt", null)
    }

    override fun setSupabaseJwt(jwt: String?) {
        prefs.edit().putString("supabase_jwt", jwt).apply()
    }

    override fun getDeviceLabel(): String {
        return prefs.getString("device_label", "Dispositivo") ?: "Dispositivo"
    }

    override fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }
}
