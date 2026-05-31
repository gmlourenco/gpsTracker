package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.BuildConfig

object ApiRoutes {
    private val BASE get() = BuildConfig.BACKEND_BASE_URL

    val LOCATION       = "$BASE/api/location"
    val EMERGENCY      = "$BASE/api/emergency"
    val DEVICE_PROFILE = "$BASE/api/devices/profile"
    val FCM_TOKEN      = "$BASE/api/devices/fcm-token"
    val POSITIONS_LAST = "$BASE/api/positions/last"
    fun appVersion(current: String) = "$BASE/api/app/version?current=$current"
}
