package com.segurancarural.gpstracker.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.segurancarural.gpstracker.data.model.TelemetryRecord

/**
 * AppDatabase — Room database definition for the local offline telemetry queue.
 *
 * Version history:
 *   v1 — Initial schema: telemetry_queue table.
 *   v3 — Added composite indexes on telemetry_queue (synced+emergencyState, synced+createdAtEpochMs).
 *
 * The concrete builder is provided by platform-specific `DatabaseDriverFactory`
 * implementations (expect/actual pattern for KMP).
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@ConstructedBy(AppDatabaseConstructor::class)
@Database(
    entities = [TelemetryRecord::class],
    version = 3,
    exportSchema = true  // Schema exported to /shared/schemas/ for migration tracking
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        const val DATABASE_NAME = "seguranca_rural_db"
    }
}
