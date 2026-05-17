package com.seguranca.rural.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seguranca.rural.data.model.TelemetryRecord
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
    @Query("UPDATE telemetry_queue SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /**
     * Permanently delete all records that have been marked as synced.
     * Called periodically to keep the local DB lean.
     */
    @Query("DELETE FROM telemetry_queue WHERE synced = 1")
    suspend fun deleteSynced(): Int

    // ── Phase 1: Emergency / SOS records (LIFO) ───────────────────────────

    /**
     * Returns all unsynced SOS records, newest first.
     * In LIFO mode, the most recent SOS position is transmitted first
     * so that family members immediately see the last known emergency location.
     */
    @Query("""
        SELECT * FROM telemetry_queue
        WHERE synced = 0 AND emergencyState = 1
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
        WHERE synced = 0
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
        WHERE synced = 0 AND emergencyState = 0
        ORDER BY createdAtEpochMs ASC
        LIMIT :limit
    """)
    suspend fun getOldestUnsyncedBatch(limit: Int = 25): List<TelemetryRecord>

    // ── Utility queries ───────────────────────────────────────────────────

    /** Count of all unsynced records — used for the connectivity badge UI. */
    @Query("SELECT COUNT(*) FROM telemetry_queue WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    /** Total number of records in the queue (synced + unsynced). */
    @Query("SELECT COUNT(*) FROM telemetry_queue")
    suspend fun getTotalCount(): Int

    /** Check if any emergency records are pending sync. */
    @Query("SELECT COUNT(*) FROM telemetry_queue WHERE synced = 0 AND emergencyState = 1")
    suspend fun getEmergencyPendingCount(): Int

    // ── Map History ───────────────────────────────────────────────────────

    /**
     * Returns a reactive flow of route history for the map, filtered by time.
     * Includes both synced and unsynced records.
     */
    @Query("SELECT * FROM telemetry_queue WHERE createdAtEpochMs >= :sinceMs ORDER BY createdAtEpochMs ASC")
    fun observeRouteHistory(sinceMs: Long): Flow<List<TelemetryRecord>>
}
