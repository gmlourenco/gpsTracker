package com.segurancarural.gpstracker.data.db

import android.content.Context
import androidx.room.Room

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
        // Allow destructive migration for MVP — switch to proper migrations before v2
        .fallbackToDestructiveMigration(dropAllTables = false)
        .build()
}
