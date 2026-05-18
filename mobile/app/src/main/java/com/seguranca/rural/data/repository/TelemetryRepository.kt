package com.seguranca.rural.data.repository

import android.content.Context
import android.util.Log
import com.seguranca.rural.BuildConfig
import com.seguranca.rural.data.db.createAppDatabase
import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.data.network.ApiClient
import com.seguranca.rural.sync.toLocationJson
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryRepository(context: Context) {
    private val dao = createAppDatabase(context.applicationContext).telemetryDao()
    private val httpClient = ApiClient.httpClient

    suspend fun submitLocation(record: TelemetryRecord) = withContext(Dispatchers.IO) {
        val payload = record.toLocationJson()
        Log.i("TelemetryRepository", "📡 Preparing to send location update...")
        Log.d("TelemetryRepository", "Payload: $payload")

        try {
            val response = httpClient.post("${BuildConfig.BACKEND_BASE_URL}/api/location") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status == HttpStatusCode.OK) {
                Log.i("TelemetryRepository", "✅ Location sent immediately! Status: 200 OK")
                // Success: write to DB marked as synced (so we have a local history but SyncWorker ignores it)
                dao.insert(record.copy(synced = true))
            } else {
                Log.e("TelemetryRepository", "❌ Network push failed: ${response.status}. Queueing for offline sync.")
                dao.insert(record.copy(synced = false))
            }
        } catch (e: Exception) {
            Log.w("TelemetryRepository", "Network exception: ${e.message}. Queueing for offline sync.")
            dao.insert(record.copy(synced = false))
        }
    }
}
