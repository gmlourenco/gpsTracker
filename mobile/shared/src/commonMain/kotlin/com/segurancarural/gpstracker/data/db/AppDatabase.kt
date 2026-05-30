package com.segurancarural.gpstracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.segurancarural.gpstracker.data.model.TelemetryRecord

/**
 * AppDatabase — Room database definition for the local offline telemetry queue.
 *
 * Version history:
 *   v1 — Initial schema: telemetry_queue table.
 *
 * The concrete builder is provided by platform-specific `DatabaseDriverFactory`
 * implementations (expect/actual pattern for KMP).
 */
@Database(
    entities = [TelemetryRecord::class],
    version = 1,
    exportSchema = true  // Schema exported to /shared/schemas/ for migration tracking
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        const val DATABASE_NAME = "seguranca_rural_db"
    }
}
