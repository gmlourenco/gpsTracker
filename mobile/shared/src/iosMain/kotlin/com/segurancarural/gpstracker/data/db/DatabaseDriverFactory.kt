package com.segurancarural.gpstracker.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.bundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS database builder using Room KMP and bundled SQLite driver.
 *
 * Stores the database in the iOS NSDocumentDirectory.
 */
@OptIn(ExperimentalForeignApi::class)
fun createAppDatabase(): AppDatabase {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    
    val dbFilePath = requireNotNull(documentDirectory?.path) { "Could not find document directory" } + "/${AppDatabase.DATABASE_NAME}"
    
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
        factory = { AppDatabase::class.instantiateImpl() } // Required for KMP Room
    )
        .setDriver(bundledSQLiteDriver())
        .fallbackToDestructiveMigration()
        .build()
}
