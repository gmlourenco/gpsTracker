package com.segurancarural.gpstracker.sync

import com.segurancarural.gpstracker.data.db.TelemetryDao
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.util.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.segurancarural.gpstracker.util.KmpLogger as Log

private const val TAG = "SyncEngine"

/**
 * SyncEngine — 3-phase asymmetric flush of the local offline telemetry queue.
 *
 * Call [flush] when network connectivity is restored. The engine will:
 *
 *   Phase 1 – Emergency records (LIFO):
 *     Transmit all SOS records immediately, newest first. Each record is sent
 *     individually to /api/emergency for dedicated handling.
 *
 *   Phase 2 – Latest current position (single record):
 *     Transmit the most recent location so the dashboard shows current position
 *     without waiting for the full history upload.
 *
 *   Phase 3 – Historical reconstruction (FIFO, batched):
 *     Transmit accumulated history in batches of 25 records to /api/location,
 *     minimising HTTP handshake overhead.
 *
 * After successful transmission, records are marked as synced and eventually
 * deleted from the local queue by [cleanupSynced].
 * @param httpClient   A configured Ktor [HttpClient].
 * @param locationUrl  URL for location updates (V2 batch endpoint).
 * @param emergencyUrl URL for emergency SOS updates.
 */
class SyncEngine(
    private val dao: TelemetryDao,
    private val httpClient: HttpClient,
    private val locationUrl: String,
    private val emergencyUrl: String,
    private val farmIdProvider: () -> String? = { null }
) {

    private val syncMutex = Mutex()

    /**
     * Executes the full 3-phase flush. Returns [SyncResult] summarising
     * how many records were synced and any errors encountered.
     */
    suspend fun flush(): SyncResult {
        if (!syncMutex.tryLock()) {
            Log.w(TAG, "Sync already in progress, skipping")
            return SyncResult(skipped = true)
        }
        try {
            return withContext(ioDispatcher) {
                var emergencySyncedCount = 0
                var latestWasSynced = false
                var historySyncedCount = 0
                var errorCount = 0

                // ── Recover stuck records ────────────────────────────────────────────────
                val recovered = dao.resetSyncingToPending()
                if (recovered > 0) {
                    Log.w(TAG, "Recovered $recovered stuck SYNCING records to PENDING")
                }

                // ── Phase 1: Emergency / SOS (LIFO) ──────────────────────────────
                val emergencyRecords = dao.getEmergencyRecords()
                if (emergencyRecords.isNotEmpty()) {
                    Log.w(TAG, "Phase 1: Flushing ${emergencyRecords.size} SOS records (LIFO)")
                    for (record in emergencyRecords) {
                        dao.markSyncing(listOf(record.id))
                        val success = transmitToEmergency(record)
                        if (success) {
                            dao.markSynced(listOf(record.id))
                            emergencySyncedCount++
                        } else {
                            dao.resetSyncingToPending() // Revert immediately
                            errorCount++
                            // Do not break — try remaining emergency records
                        }
                    }
                }

                // ── Phase 2: Latest current position ─────────────────────────────
                val latestRecord = dao.getLatestUnsynced()
                if (latestRecord != null) {
                    Log.d(TAG, "Phase 2: Sending latest position (id=${latestRecord.id})")
                    dao.markSyncing(listOf(latestRecord.id))
                    val success = transmitBatchToLocation(listOf(latestRecord))
                    if (success) {
                        dao.markSynced(listOf(latestRecord.id))
                        latestWasSynced = true
                    } else {
                        dao.resetSyncingToPending()
                        errorCount++
                    }
                }

                // ── Phase 3: Historical FIFO batches ──────────────────────────────
                var hasMore = true
                while (hasMore) {
                    val batch = dao.getOldestUnsyncedBatch(limit = 25)
                    if (batch.isEmpty()) {
                        hasMore = false
                        break
                    }

                    Log.d(TAG, "Phase 3: Transmitting batch of ${batch.size} records in a single V2 request (FIFO)")
                    val successIds = batch.map { it.id }
                    dao.markSyncing(successIds)
                    val success = transmitBatchToLocation(batch)
                    if (success) {
                        dao.markSynced(successIds)
                        historySyncedCount += batch.size
                    } else {
                        dao.resetSyncingToPending()
                        errorCount++
                        // Stop batching on first failure to avoid out-of-order gaps
                        hasMore = false
                    }
                }

                // ── Cleanup synced records ────────────────────────────────────────
                val deleted = dao.deleteSynced()
                Log.d(TAG, "Cleanup: deleted $deleted synced records from queue")

                val result = SyncResult(
                    emergencySynced = emergencySyncedCount,
                    latestSynced = latestWasSynced,
                    historySynced = historySyncedCount,
                    errors = errorCount,
                )

                Log.i(
                    TAG,
                    "Flush complete — SOS: ${result.emergencySynced}, " +
                    "Latest: ${result.latestSynced}, " +
                    "History: ${result.historySynced}, " +
                    "Errors: ${result.errors}"
                )

                result
            }
        } finally {
            syncMutex.unlock()
        }
    }

    // ── Private transmission helpers ─────────────────────────────────────────

    private suspend fun transmitToEmergency(record: TelemetryRecord): Boolean {
        return try {
            val response = httpClient.post(emergencyUrl) {
                contentType(ContentType.Application.Json)
                setBody(record.toEmergencyJson())
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val isLogicalSuccess = try {
                    val json = Json.parseToJsonElement(body)
                    json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
                } catch (e: Exception) {
                    false
                }
                if (isLogicalSuccess) {
                    true
                } else {
                    Log.e(TAG, "Emergency TX returned HTTP 200 but logical success=false or invalid response: $body")
                    false
                }
            } else {
                Log.e(TAG, "Emergency TX failed: ${response.status} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency TX exception: ${e.message}", e)
            false
        }
    }

    private suspend fun transmitBatchToLocation(records: List<TelemetryRecord>): Boolean {
        return try {
            val response = httpClient.post(locationUrl) {
                contentType(ContentType.Application.Json)
                setBody(records.toLocationV2Json(farmId = farmIdProvider()))
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val isLogicalSuccess = try {
                    val json = Json.parseToJsonElement(body)
                    json.jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
                } catch (e: Exception) {
                    false
                }
                if (isLogicalSuccess) {
                    true
                } else {
                    Log.e(TAG, "Location V2 Batch TX returned HTTP 200 but logical success=false or invalid response: $body")
                    false
                }
            } else {
                Log.e(TAG, "Location V2 Batch TX failed: ${response.status} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location V2 Batch TX exception: ${e.message}", e)
            false
        }
    }
}

// ── JSON serialisation helpers ─────────────────────────────────────────────

/**
 * Converts a batch of TelemetryRecords to the JSON format expected by POST /api/v2/location.
 */
fun List<TelemetryRecord>.toLocationV2Json(markerColorHex: String? = null, farmId: String? = null): String {
    if (isEmpty()) return "{}"
    val first = first()
    return buildJsonObject {
        put("id", first.serialNumber)
        put("deviceLabel", first.deviceLabel)
        farmId?.let { put("farmId", it) }
        putJsonArray("locations") {
            forEach { record ->
                addJsonObject {
                    put("timestamp", record.timestamp)
                    put("batteryLevel", record.batteryLevel)
                    put("batteryCharging", record.batteryCharging)
                    putJsonObject("gps") {
                        put("lat", record.lat)
                        put("lng", record.lng)
                        put("accuracy", record.accuracy)
                        put("speed", record.speed)
                        put("heading", record.heading)
                    }
                    put("emergencyState", record.emergencyState)
                    put("trackingEnabled", record.trackingEnabled)
                    put("networkType", record.networkType)
                    put("appVersion", record.appVersion)
                    markerColorHex?.let { put("markerColor", it) }
                }
            }
        }
    }.let { Json.encodeToString(it) }
}

/**
 * Converts a TelemetryRecord to the JSON format expected by POST /api/location.
 */
fun TelemetryRecord.toLocationJson(markerColorHex: String? = null): String = buildJsonObject {
    put("serialNumber", serialNumber)
    put("deviceLabel", deviceLabel)
    put("timestamp", timestamp)
    put("batteryLevel", batteryLevel)
    put("batteryCharging", batteryCharging)
    putJsonObject("gps") {
        put("lat", lat)
        put("lng", lng)
        put("accuracy", accuracy)
        put("speed", speed)
        put("heading", heading)
    }
    put("emergencyState", emergencyState)
    put("trackingEnabled", trackingEnabled)
    put("networkType", networkType)
    put("appVersion", appVersion)
    markerColorHex?.let { put("markerColor", it) }
}.let { Json.encodeToString(it) }

/**
 * Converts a TelemetryRecord to the JSON format expected by POST /api/emergency.
 * The emergency endpoint does not include `emergencyState` or `trackingEnabled` fields.
 */
fun TelemetryRecord.toEmergencyJson(): String = buildJsonObject {
    put("serialNumber", serialNumber)
    put("deviceLabel", deviceLabel)
    put("timestamp", timestamp)
    put("batteryLevel", batteryLevel)
    put("batteryCharging", batteryCharging)
    putJsonObject("gps") {
        put("lat", lat)
        put("lng", lng)
        put("accuracy", accuracy)
        put("speed", speed)
        put("heading", heading)
    }
    put("networkType", networkType)
    put("appVersion", appVersion)
}.let { Json.encodeToString(it) }

// ── Result data class ──────────────────────────────────────────────────────

/**
 * Summary of a single [SyncEngine.flush] execution.
 */
data class SyncResult(
    val emergencySynced: Int = 0,
    val latestSynced: Boolean = false,
    val historySynced: Int = 0,
    val errors: Int = 0,
    val skipped: Boolean = false,
) {
    val totalSynced: Int get() = emergencySynced + (if (latestSynced) 1 else 0) + historySynced
    val hasErrors: Boolean get() = errors > 0
}
