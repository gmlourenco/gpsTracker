package com.segurancarural.gpstracker.data.db

import android.content.Context
import androidx.room.Room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes required for v1 to v2, just version bump
    }
}

/**
 * Android-specific Room database builder.
 *
 * Creates the [AppDatabase] instance using Room's Android builder,
 * writing the database file to the app's private data directory.
 *
 * @param context Application context.
 */
fun createAppDatabase(context: Context): AppDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
}
