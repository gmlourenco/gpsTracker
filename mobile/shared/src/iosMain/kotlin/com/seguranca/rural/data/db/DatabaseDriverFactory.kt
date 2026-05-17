package com.seguranca.rural.data.db

/**
 * iOS placeholder — Phase 3 implementation.
 *
 * When the iOS port is implemented, this file will provide the
 * SQLite driver backed by the iOS file system using the Room
 * KMP iOS driver or SQLDelight equivalent.
 *
 * Current state: throws UnsupportedOperationException to prevent
 * accidental use before the iOS port is complete.
 */
fun createAppDatabase(): AppDatabase {
    throw UnsupportedOperationException(
        "iOS database driver is not yet implemented. " +
        "This is a Phase 3 deliverable. " +
        "See .ai/plan.md for the iOS port roadmap."
    )
}
