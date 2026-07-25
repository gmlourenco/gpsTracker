package com.segurancarural.gpstracker

interface PlatformDependencies {
    fun shouldUploadOverCurrentNetwork(): Boolean
    fun getDeviceMarkerColorHex(): String?
    fun ensureSerialNumber(): String
    fun getOfflineQueueJson(): String?
    fun saveOfflineQueueJson(json: String)
    
    fun getFarmId(): String?
    fun setFarmId(farmId: String?)
    fun getSupabaseJwt(): String?
    fun setSupabaseJwt(jwt: String?)
    fun getDeviceLabel(): String
    fun getAppVersion(): String
}

object Platform {
    lateinit var dependencies: PlatformDependencies
}
