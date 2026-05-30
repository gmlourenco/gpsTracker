package com.seguranca.rural.data.repository

import android.content.Context
import com.seguranca.rural.BuildConfig
import com.seguranca.rural.util.AppLog
import com.seguranca.rural.data.db.createAppDatabase
import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.data.network.ApiClient
import com.seguranca.rural.sync.toLocationJson
import com.seguranca.rural.util.argbToMapLibreHex
import com.seguranca.rural.util.deviceMarkerColorArgb
import com.seguranca.rural.util.shouldUploadOverCurrentNetwork
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val dao = createAppDatabase(appContext).telemetryDao()
    private val httpClient = ApiClient.httpClient

    suspend fun submitLocation(record: TelemetryRecord) = withContext(Dispatchers.IO) {
        if (!shouldUploadOverCurrentNetwork(appContext)) {
            AppLog.d("TelemetryRepository", "Mobile data sync disabled — queueing locally")
            dao.insert(record.copy(synced = false))
            return@withContext
        }

        val payload = record.toLocationJson(argbToMapLibreHex(appContext.deviceMarkerColorArgb()))
        AppLog.i("TelemetryRepository", "Preparing to send location update...")
        AppLog.d("TelemetryRepository", "Payload: $payload")

        try {
            val response = httpClient.post("${BuildConfig.BACKEND_BASE_URL}/api/location") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            // Consume the body to release the HTTP connection back to the pool.
            // Not doing this causes "A resource failed to call close" warnings.
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.OK) {
                AppLog.i("TelemetryRepository", "Location sent — 200 OK: $responseBody")
                dao.insert(record.copy(synced = true))
            } else {
                AppLog.e("TelemetryRepository", "Network push failed: ${response.status} — $responseBody")
                dao.insert(record.copy(synced = false))
            }
        } catch (e: Exception) {
            AppLog.w("TelemetryRepository", "Network exception: ${e.message}", e)
            dao.insert(record.copy(synced = false))
        }
    }
}
