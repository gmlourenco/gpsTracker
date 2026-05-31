package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.sync.toLocationJson
import com.segurancarural.gpstracker.util.argbToMapLibreHex
import com.segurancarural.gpstracker.util.deviceMarkerColorArgb
import com.segurancarural.gpstracker.util.shouldUploadOverCurrentNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val dao = createAppDatabase(appContext).telemetryDao()
    private val apiService = ApiService()

    suspend fun submitLocation(record: TelemetryRecord) = withContext(Dispatchers.IO) {
        if (!shouldUploadOverCurrentNetwork(appContext)) {
            AppLog.d("TelemetryRepository", "Mobile data sync disabled — queueing locally")
            dao.insert(record.copy(synced = false))
            return@withContext
        }

        val payload = record.toLocationJson(argbToMapLibreHex(appContext.deviceMarkerColorArgb()))
        AppLog.i("TelemetryRepository", "Preparing to send location update...")
        AppLog.d("TelemetryRepository", "Payload: $payload")

        val result = apiService.postRaw(ApiRoutes.LOCATION, payload)

        when (result) {
            is ApiResult.Success -> {
                AppLog.i("TelemetryRepository", "Location sent — 200 OK: ${result.data}")
                dao.insert(record.copy(synced = true))
            }
            is ApiResult.HttpError -> {
                AppLog.e("TelemetryRepository", "Network push failed: ${result.code} — ${result.message}")
                dao.insert(record.copy(synced = false))
            }
            is ApiResult.NetworkError -> {
                AppLog.w("TelemetryRepository", "Network exception: ${result.exception.message}", result.exception)
                dao.insert(record.copy(synced = false))
            }
            is ApiResult.Unauthorized -> {
                AppLog.e("TelemetryRepository", "Unauthorized push attempt")
                dao.insert(record.copy(synced = false))
            }
        }
    }
}
