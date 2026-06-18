package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.BuildConfig

object ApiRoutes {
    private val BASE get() = BuildConfig.BACKEND_BASE_URL

    val LOCATION_V2    = "$BASE/api/v2/location"
    val EMERGENCY      = "$BASE/api/emergency"
    val DEVICE_CONFIG  = "$BASE/api/devices/config"
    val FCM_TOKEN      = "$BASE/api/devices/fcm-token"
    val POSITIONS_LAST = "$BASE/api/positions/last"
    fun appVersion(current: String) = "$BASE/api/app/version?current=$current"
}
