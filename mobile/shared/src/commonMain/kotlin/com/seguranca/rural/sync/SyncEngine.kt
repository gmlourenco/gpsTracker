package com.seguranca.rural.sync

import android.util.Log
import com.seguranca.rural.data.db.TelemetryDao
import com.seguranca.rural.data.model.TelemetryRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
 *
 * @param dao          The [TelemetryDao] for queue access.
 * @param httpClient   A configured Ktor [HttpClient].
 * @param backendBaseUrl Base URL of the Next.js backend (e.g., "https://example.vercel.app").
 * @param deviceApiSecret The shared bearer token for API authorization.
 */
class SyncEngine(
    private val dao: TelemetryDao,
    private val httpClient: HttpClient,
    private val backendBaseUrl: String,
    private val deviceApiSecret: String,
) {

    private val locationUrl = "$backendBaseUrl/api/location"
    private val emergencyUrl = "$backendBaseUrl/api/emergency"

    /**
     * Executes the full 3-phase flush. Returns [SyncResult] summarising
     * how many records were synced and any errors encountered.
     */
    suspend fun flush(): SyncResult = withContext(Dispatchers.IO) {
        val result = SyncResult()

        // ── Phase 1: Emergency / SOS (LIFO) ──────────────────────────────
        val emergencyRecords = dao.getEmergencyRecords()
        if (emergencyRecords.isNotEmpty()) {
            Log.w(TAG, "Phase 1: Flushing ${emergencyRecords.size} SOS records (LIFO)")
            for (record in emergencyRecords) {
                val success = transmitToEmergency(record)
                if (success) {
                    dao.markSynced(listOf(record.id))
                    result.emergencySynced++
                } else {
                    result.errors++
                    // Do not break — try remaining emergency records
                }
            }
        }

        // ── Phase 2: Latest current position ─────────────────────────────
        val latestRecord = dao.getLatestUnsynced()
        if (latestRecord != null) {
            Log.d(TAG, "Phase 2: Sending latest position (id=${latestRecord.id})")
            val success = transmitToLocation(latestRecord)
            if (success) {
                dao.markSynced(listOf(latestRecord.id))
                result.latestSynced = true
            } else {
                result.errors++
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

            Log.d(TAG, "Phase 3: Transmitting batch of ${batch.size} records (FIFO)")
            val successIds = mutableListOf<Long>()
            for (record in batch) {
                if (transmitToLocation(record)) {
                    successIds.add(record.id)
                    result.historySynced++
                } else {
                    result.errors++
                    // Stop batching on first failure to avoid out-of-order gaps
                    hasMore = false
                    break
                }
            }
            if (successIds.isNotEmpty()) {
                dao.markSynced(successIds)
            }
        }

        // ── Cleanup synced records ────────────────────────────────────────
        val deleted = dao.deleteSynced()
        Log.d(TAG, "Cleanup: deleted $deleted synced records from queue")

        Log.i(
            TAG,
            "Flush complete — SOS: ${result.emergencySynced}, " +
            "Latest: ${result.latestSynced}, " +
            "History: ${result.historySynced}, " +
            "Errors: ${result.errors}"
        )

        result
    }

    // ── Private transmission helpers ─────────────────────────────────────────

    private suspend fun transmitToEmergency(record: TelemetryRecord): Boolean {
        return try {
            val response = httpClient.post(emergencyUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(deviceApiSecret)
                setBody(record.toEmergencyJson())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Log.e(TAG, "Emergency TX failed: ${response.status} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency TX exception: ${e.message}", e)
            false
        }
    }

    private suspend fun transmitToLocation(record: TelemetryRecord): Boolean {
        return try {
            val response = httpClient.post(locationUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(deviceApiSecret)
                setBody(record.toLocationJson())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Log.e(TAG, "Location TX failed: ${response.status} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location TX exception: ${e.message}", e)
            false
        }
    }
}

// ── JSON serialisation helpers ─────────────────────────────────────────────

/**
 * Converts a TelemetryRecord to the JSON format expected by POST /api/location.
 */
fun TelemetryRecord.toLocationJson(): String = buildJsonObject {
    put("deviceId", deviceId)
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
}.let { Json.encodeToString(it) }

/**
 * Converts a TelemetryRecord to the JSON format expected by POST /api/emergency.
 * The emergency endpoint does not include `emergencyState` or `trackingEnabled` fields.
 */
fun TelemetryRecord.toEmergencyJson(): String = buildJsonObject {
    put("deviceId", deviceId)
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
    var emergencySynced: Int = 0,
    var latestSynced: Boolean = false,
    var historySynced: Int = 0,
    var errors: Int = 0,
) {
    val totalSynced: Int get() = emergencySynced + (if (latestSynced) 1 else 0) + historySynced
    val hasErrors: Boolean get() = errors > 0
}
