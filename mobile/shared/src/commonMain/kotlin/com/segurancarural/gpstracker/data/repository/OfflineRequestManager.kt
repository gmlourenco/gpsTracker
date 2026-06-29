package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.Platform
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.util.KmpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

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
    private val apiService = ApiService()

    private fun loadQueue(): MutableList<PendingRequest> {
        val json = Platform.dependencies.getOfflineQueueJson() ?: return mutableListOf()
        return try {
            Json.decodeFromString<List<PendingRequest>>(json).toMutableList()
        } catch (e: Exception) {
            KmpLogger.e("OfflineRequestManager", "Failed to deserialize queue", e)
            mutableListOf()
        }
    }

    private fun saveQueue(queue: List<PendingRequest>) {
        try {
            val json = Json.encodeToString(queue)
            Platform.dependencies.saveOfflineQueueJson(json)
        } catch (e: Exception) {
            KmpLogger.e("OfflineRequestManager", "Failed to serialize queue", e)
        }
    }

    fun enqueue(serviceType: String, url: String, method: String, bodyJson: String) {
        val queue = loadQueue()
        val timestamp = com.segurancarural.gpstracker.util.currentTimeMillis()
        val randomId = Random.nextLong().toString()
        val request = PendingRequest(
            id = randomId,
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
        saveQueue(queue)
        KmpLogger.i("OfflineRequestManager", "Enqueued pending request: type=$serviceType, url=$url")
    }

    fun hasPending(serviceType: String): Boolean {
        return loadQueue().any { it.serviceType == serviceType }
    }

    fun clearPending(serviceType: String) {
        val queue = loadQueue()
        if (queue.removeAll { it.serviceType == serviceType }) {
            saveQueue(queue)
            KmpLogger.i("OfflineRequestManager", "Cleared pending requests for type: $serviceType")
        }
    }

    @Throws(Exception::class)
    suspend fun processQueue() = withContext(Dispatchers.Default) {
        val queue = loadQueue()
        if (queue.isEmpty()) return@withContext

        KmpLogger.i("OfflineRequestManager", "Processing offline queue (${queue.size} requests)...")
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val request = iterator.next()
            KmpLogger.d("OfflineRequestManager", "Retrying request: id=${request.id}, type=${request.serviceType}")

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
                        KmpLogger.i("OfflineRequestManager", "Successfully synced request: id=${request.id}")
                        iterator.remove()
                        requestSucceeded = true
                    } else {
                        KmpLogger.e("OfflineRequestManager", "Request succeeded with HTTP 200 but returned logical success=false: $body", null)
                    }
                }
                is ApiResult.HttpError -> {
                    KmpLogger.e("OfflineRequestManager", "HTTP error ${result.code} retrying request: id=${request.id}", null)
                }
                is ApiResult.NetworkError -> {
                    KmpLogger.w("OfflineRequestManager", "Network error retrying request: id=${request.id}")
                }
                is ApiResult.Unauthorized -> {
                    KmpLogger.e("OfflineRequestManager", "Unauthorized error retrying request: id=${request.id}", null)
                }
            }

            if (!requestSucceeded) {
                break
            }
        }

        saveQueue(queue)
    }

    fun clearQueue() {
        saveQueue(emptyList())
        KmpLogger.i("OfflineRequestManager", "Offline request queue cleared")
    }
}
