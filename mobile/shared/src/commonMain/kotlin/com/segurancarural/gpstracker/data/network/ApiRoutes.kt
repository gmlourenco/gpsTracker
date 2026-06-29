package com.segurancarural.gpstracker.data.network

object ApiRoutes {
    val BASE: String get() = SharedConfig.BACKEND_BASE_URL.removeSuffix("/")

    val LOCATION_V2: String get() = "$BASE/api/v2/location"
    val EMERGENCY: String get() = "$BASE/api/emergency"
    val TELEMETRY_BATCH: String get() = "$BASE/api/v2/telemetry"
    val DEVICE_CONFIG: String get() = "$BASE/api/devices/config"
    val FCM_TOKEN: String get() = "$BASE/api/devices/fcm-token"
    val POSITIONS_LAST: String get() = "$BASE/api/positions/last"
    fun positionsLast(history: Int) = "$BASE/api/positions/last?history=$history"
    fun appVersion(current: String) = "$BASE/api/app/version?current=$current"
}
