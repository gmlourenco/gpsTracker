package com.seguranca.rural.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TelemetryRecord — the local Room entity that mirrors the telemetry JSON payload.
 *
 * Every GPS fix, heartbeat, or SOS activation is stored here before being
 * synchronised to the backend. This is the single source of truth for the
 * offline queue.
 *
 * Sync priority logic (handled by SyncEngine):
 *   - emergency_state = TRUE → LIFO (most recent SOS first)
 *   - Normal records        → FIFO (oldest first, in batches of 25)
 *   - Latest point          → The single newest record (synced first, before history)
 */
@Entity(tableName = "telemetry_queue")
data class TelemetryRecord(

    /** Auto-generated local ID. Used for FIFO ordering. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** UUID identifying this device. Set once on first run. */
    val deviceId: String,

    /** Human-readable label (e.g., "Trator-Pai"). */
    val deviceLabel: String,

    /** ISO 8601 timestamp from device at point of GPS capture. */
    val timestamp: String,

    /** Battery level 0–100. */
    val batteryLevel: Int,

    /** Whether the device is currently charging. */
    val batteryCharging: Boolean,

    // ── GPS fields (flattened for Room column efficiency) ──

    val lat: Double,
    val lng: Double,

    /** Horizontal accuracy radius in metres. Values > 80m are low-quality. */
    val accuracy: Float,

    /** Ground speed in km/h. */
    val speed: Float,

    /** Compass heading in degrees (0–360). */
    val heading: Float,

    /** TRUE when the SOS emergency button has been activated. */
    val emergencyState: Boolean,

    /** Whether tracking was enabled by the user at time of capture. */
    val trackingEnabled: Boolean,

    /** Network type at time of capture: "WIFI", "4G", "3G", "2G", "NONE". */
    val networkType: String,

    /** App version string for server-side debugging. */
    val appVersion: String,

    /**
     * Epoch milliseconds when this record was created locally.
     * Used to determine FIFO ordering and heartbeat intervals.
     */
    val createdAtEpochMs: Long = System.currentTimeMillis(),

    /**
     * Whether this record has been successfully synced to the backend.
     * SyncEngine queries only records where synced = false.
     */
    val synced: Boolean = false
)
