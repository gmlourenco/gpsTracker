package com.segurancarural.gpstracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import kotlinx.coroutines.flow.Flow

/**
 * TelemetryDao — Data Access Object for the offline telemetry queue.
 *
 * Query strategies match the 3-phase SyncEngine flush policy:
 *   Phase 1 — LIFO for emergencies (getEmergencyRecords)
 *   Phase 2 — Latest unsynced point (getLatestUnsynced)
 *   Phase 3 — FIFO batches for history (getOldestUnsyncedBatch)
 */
@Dao
interface TelemetryDao {

    // ── Write operations ──────────────────────────────────────────────────

    /**
     * Insert a new telemetry record into the queue.
     * IGNORE strategy: if an identical record is inserted twice, silently skip.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: TelemetryRecord): Long

    /**
     * Mark one or more records as successfully synced.
     * The SyncEngine calls this after confirming the backend returned 200.
     */
    @Query("UPDATE telemetry_queue SET syncState = 1 WHERE id IN (:ids)")
    suspend fun markSyncing(ids: List<Long>)

    /**
     * Mark one or more records as successfully synced.
     */
    @Query("UPDATE telemetry_queue SET syncState = 2 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /**
     * Revert records that were syncing back to pending (on network failure or startup crash recovery).
     */
    @Query("UPDATE telemetry_queue SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToPending(): Int

    /**
     * Revert specific records that were syncing back to pending (on transmission failure).
     */
    @Query("UPDATE telemetry_queue SET syncState = 0 WHERE id IN (:ids)")
    suspend fun resetSyncingToPendingByIds(ids: List<Long>): Int

    /**
     * Permanently delete all records that have been marked as synced.
     * Called periodically to keep the local DB lean.
     */
    @Query("DELETE FROM telemetry_queue WHERE syncState = 2")
    suspend fun deleteSynced(): Int

    // ── Phase 1: Emergency / SOS records (LIFO) ───────────────────────────

    /**
     * Returns all unsynced SOS records, newest first.
     * In LIFO mode, the most recent SOS position is transmitted first
     * so that family members immediately see the last known emergency location.
     */
    @Query("""
        SELECT * FROM telemetry_queue
        WHERE syncState = 0 AND emergencyState = 1
        ORDER BY createdAtEpochMs DESC
    """)
    suspend fun getEmergencyRecords(): List<TelemetryRecord>

    // ── Phase 2: Latest known position ───────────────────────────────────

    /**
     * Returns the single most recent unsynced record (any type).
     * Transmitted before the historical batch so that the dashboard shows
     * current position without waiting for the full history to upload.
     */
    @Query("""
        SELECT * FROM telemetry_queue
        WHERE syncState = 0
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
    """)
    suspend fun getLatestUnsynced(): TelemetryRecord?

    // ── Phase 3: Historical FIFO batches ──────────────────────────────────

    /**
     * Returns the oldest [limit] unsynced normal records (FIFO).
     * Excludes emergency records (already handled in Phase 1).
     * Sent in batches of 25 to reduce HTTP overhead.
     */
    @Query("""
        SELECT * FROM telemetry_queue
        WHERE syncState = 0 AND emergencyState = 0
        ORDER BY createdAtEpochMs ASC
        LIMIT :limit
    """)
    suspend fun getOldestUnsyncedBatch(limit: Int = 25): List<TelemetryRecord>

    // ── Utility queries ───────────────────────────────────────────────────

    /** Count of all unsynced records — used for the connectivity badge UI. */
    /** Count of all unsynced records — used for the connectivity badge UI. */
    @Query("SELECT COUNT(*) FROM telemetry_queue WHERE syncState = 0")
    fun observeUnsyncedCount(): Flow<Int>

    /** Number of records still waiting to be uploaded. */
    @Query("SELECT COUNT(*) FROM telemetry_queue WHERE syncState = 0")
    suspend fun getUnsyncedCount(): Int

    /** Total number of records in the queue (synced + unsynced). */
    @Query("SELECT COUNT(*) FROM telemetry_queue")
    suspend fun getTotalCount(): Int

    /** Check if any emergency records are pending sync. */
    @Query("SELECT COUNT(*) FROM telemetry_queue WHERE syncState = 0 AND emergencyState = 1")
    suspend fun getEmergencyPendingCount(): Int

    // ── Map History ───────────────────────────────────────────────────────

    /**
     * Returns a reactive flow of route history for the map, filtered by time.
     * Includes both synced and unsynced records.
     */
    @Query("SELECT * FROM telemetry_queue WHERE createdAtEpochMs >= :sinceMs ORDER BY createdAtEpochMs ASC")
    fun observeRouteHistory(sinceMs: Long): Flow<List<TelemetryRecord>>

    /**
     * Returns a reactive flow of route history for the map, filtered by explicit time boundaries.
     */
    @Query("SELECT * FROM telemetry_queue WHERE createdAtEpochMs >= :startMs AND createdAtEpochMs <= :endMs ORDER BY createdAtEpochMs ASC")
    fun observeRouteHistoryBounded(startMs: Long, endMs: Long): Flow<List<TelemetryRecord>>

    // ── Cleanup ───────────────────────────────────────────────────────────

    /**
     * Deletes all records for a given serialNumber that have not yet been synced.
     * Used on startup to purge stale records saved with a placeholder serialNumber
     * (e.g., "unknown-device-id") before the real identity was established.
     */
    @Query("DELETE FROM telemetry_queue WHERE serialNumber = :serialNumber AND syncState = 0")
    suspend fun deleteUnsyncedBySerialNumber(serialNumber: String): Int
}
