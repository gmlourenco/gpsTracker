package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.Platform
import com.segurancarural.gpstracker.data.db.TelemetryDao
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.sync.toLocationV2Json
import com.segurancarural.gpstracker.util.KmpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TelemetryRepository(private val dao: TelemetryDao) {
    /** Dedicated telemetry API service — uses device secret auth only, no JWT */
    private val apiService = ApiService(client = ApiClient.telemetryClient)

    fun getUnsyncedCountFlow() = dao.observeUnsyncedCount()

    @Throws(Exception::class)
    suspend fun submitLocation(record: TelemetryRecord) = withContext(Dispatchers.Default) {
        if (!Platform.dependencies.shouldUploadOverCurrentNetwork()) {
            KmpLogger.d("TelemetryRepository", "Mobile data sync disabled — queueing locally")
            dao.insert(record.copy(syncState = 0))
            return@withContext
        }

        val markerColorHex = Platform.dependencies.getDeviceMarkerColorHex()
        val payload = listOf(record).toLocationV2Json(markerColorHex = markerColorHex)
        KmpLogger.i("TelemetryRepository", "Preparing to send location update...")
        KmpLogger.d("TelemetryRepository", "Payload: $payload")

        val result = apiService.postRaw(ApiRoutes.LOCATION_V2, payload)

        when (result) {
            is ApiResult.Success -> {
                val body = result.data
                val isLogicalSuccess = try {
                    val json = Json.parseToJsonElement(body)
                    json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
                } catch (e: Exception) {
                    false
                }
                if (isLogicalSuccess) {
                    KmpLogger.i("TelemetryRepository", "Location sent successfully: $body")
                    dao.insert(record.copy(syncState = 2))
                } else {
                    KmpLogger.w("TelemetryRepository", "Location push response was not a logical success (possibly captive portal): $body")
                    dao.insert(record.copy(syncState = 0))
                }
            }
            is ApiResult.HttpError -> {
                KmpLogger.e("TelemetryRepository", "Network push failed: ${result.code} — ${result.message}", null)
                dao.insert(record.copy(syncState = 0))
            }
            is ApiResult.NetworkError -> {
                KmpLogger.w("TelemetryRepository", "Network exception: ${result.exception.message}")
                dao.insert(record.copy(syncState = 0))
            }
            is ApiResult.Unauthorized -> {
                KmpLogger.e("TelemetryRepository", "Unauthorized push attempt", null)
                dao.insert(record.copy(syncState = 0))
            }
        }
    }
}
