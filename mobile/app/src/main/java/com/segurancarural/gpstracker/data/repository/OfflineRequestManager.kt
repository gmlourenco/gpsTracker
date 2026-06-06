package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.util.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class PendingRequest(
    val id: String,
    val serviceType: String,
    val url: String,
    val method: String,
    val bodyJson: String,
    val timestamp: Long
)

object OfflineRequestManager {
    private const val PREFS_NAME = "offline_request_prefs"
    private const val KEY_REQUEST_QUEUE = "request_queue"
    private val apiService = ApiService()

    private fun loadQueue(context: Context): MutableList<PendingRequest> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REQUEST_QUEUE, null) ?: return mutableListOf()
        return try {
            Json.decodeFromString<List<PendingRequest>>(json).toMutableList()
        } catch (e: Exception) {
            AppLog.e("OfflineRequestManager", "Failed to deserialize queue", e)
            mutableListOf()
        }
    }

    private fun saveQueue(context: Context, queue: List<PendingRequest>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val json = Json.encodeToString(queue)
            prefs.edit().putString(KEY_REQUEST_QUEUE, json).apply()
        } catch (e: Exception) {
            AppLog.e("OfflineRequestManager", "Failed to serialize queue", e)
        }
    }

    /**
     * Enqueues a request.
     * Unique-only service types (like CONFIG or FCM_TOKEN) overwrite any existing pending requests of the same type.
     */
    fun enqueue(context: Context, serviceType: String, url: String, method: String, bodyJson: String) {
        val queue = loadQueue(context)
        val timestamp = System.currentTimeMillis()
        val request = PendingRequest(
            id = java.util.UUID.randomUUID().toString(),
            serviceType = serviceType,
            url = url,
            method = method,
            bodyJson = bodyJson,
            timestamp = timestamp
        )

        if (serviceType == "CONFIG" || serviceType == "FCM_TOKEN") {
            queue.removeAll { it.serviceType == serviceType }
        }

        queue.add(request)
        saveQueue(context, queue)
        AppLog.i("OfflineRequestManager", "Enqueued pending request: type=$serviceType, url=$url")
    }

    /**
     * Checks if there are any pending requests of the given service type.
     */
    fun hasPending(context: Context, serviceType: String): Boolean {
        return loadQueue(context).any { it.serviceType == serviceType }
    }

    /**
     * Clears any pending requests of the given service type.
     */
    fun clearPending(context: Context, serviceType: String) {
        val queue = loadQueue(context)
        if (queue.removeAll { it.serviceType == serviceType }) {
            saveQueue(context, queue)
            AppLog.i("OfflineRequestManager", "Cleared pending requests for type: $serviceType")
        }
    }

    /**
     * Processes all pending requests in the queue.
     * Retries each request. If it succeeds or returns a non-retriable error (HTTP 4xx), it is removed.
     * If a request encounters a retriable error (NetworkError or HTTP 5xx), it remains in the queue, and processing halts.
     */
    suspend fun processQueue(context: Context) = withContext(Dispatchers.IO) {
        val queue = loadQueue(context)
        if (queue.isEmpty()) return@withContext

        AppLog.i("OfflineRequestManager", "Processing offline queue (${queue.size} requests)...")
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val request = iterator.next()
            AppLog.d("OfflineRequestManager", "Retrying request: id=${request.id}, type=${request.serviceType}")

            val result = when (request.method.uppercase()) {
                "POST" -> apiService.postRaw(request.url, request.bodyJson)
                "PATCH" -> apiService.patchRaw(request.url, request.bodyJson)
                else -> ApiResult.HttpError(405, "Method Not Allowed")
            }

            var requestSucceeded = false

            when (result) {
                is ApiResult.Success -> {
                    val body = result.data
                    val isLogicalSuccess = try {
                        val json = Json.parseToJsonElement(body)
                        json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull ?: true
                    } catch (e: Exception) {
                        true
                    }

                    if (isLogicalSuccess) {
                        AppLog.i("OfflineRequestManager", "Successfully synced request: id=${request.id}")
                        iterator.remove()
                        requestSucceeded = true
                    } else {
                        AppLog.e("OfflineRequestManager", "Request succeeded with HTTP 200 but returned logical success=false: $body")
                    }
                }
                is ApiResult.HttpError -> {
                    AppLog.e("OfflineRequestManager", "HTTP error ${result.code} retrying request: id=${request.id}")
                }
                is ApiResult.NetworkError -> {
                    AppLog.w("OfflineRequestManager", "Network error retrying request: id=${request.id}")
                }
                is ApiResult.Unauthorized -> {
                    AppLog.e("OfflineRequestManager", "Unauthorized error retrying request: id=${request.id}")
                }
            }

            if (!requestSucceeded) {
                // If a request fails, we halt processing of the rest of the queue to maintain ordering and avoid spamming
                break
            }
        }

        saveQueue(context, queue)
    }

    fun clearQueue(context: Context) {
        saveQueue(context, emptyList())
        AppLog.i("OfflineRequestManager", "Offline request queue cleared")
    }
}
