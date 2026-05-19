package com.seguranca.rural.data.repository

import android.content.Context
import android.util.Log
import com.seguranca.rural.BuildConfig
import com.seguranca.rural.data.db.createAppDatabase
import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.data.network.ApiClient
import com.seguranca.rural.sync.toLocationJson
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
            Log.d("TelemetryRepository", "Mobile data sync disabled — queueing locally")
            dao.insert(record.copy(synced = false))
            return@withContext
        }

        val payload = record.toLocationJson()
        Log.i("TelemetryRepository", "📡 Preparing to send location update...")
        Log.d("TelemetryRepository", "Payload: $payload")

        try {
            val response = httpClient.post("${BuildConfig.BACKEND_BASE_URL}/api/location") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            // Consume the body to release the HTTP connection back to the pool.
            // Not doing this causes "A resource failed to call close" warnings.
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.OK) {
                Log.i("TelemetryRepository", "✅ Location sent! Status: 200 OK — $responseBody")
                dao.insert(record.copy(synced = true))
            } else {
                Log.e("TelemetryRepository", "❌ Network push failed: ${response.status} — $responseBody. Queueing for offline sync.")
                dao.insert(record.copy(synced = false))
            }
        } catch (e: Exception) {
            Log.w("TelemetryRepository", "Network exception: ${e.message}. Queueing for offline sync.")
            dao.insert(record.copy(synced = false))
        }
    }
}
