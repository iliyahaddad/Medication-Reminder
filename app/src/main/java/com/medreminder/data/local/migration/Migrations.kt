package com.medreminder.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: adds `repeat_count` to `dose_logs`.
 *
 * The column is `NOT NULL DEFAULT 0`, so existing rows are back-filled safely
 * and no data rewriting is required. Verified by MigrationTest (Room test
 * harness with schemas pulled from the androidTest assets directory).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE dose_logs ADD COLUMN repeat_count INTEGER NOT NULL DEFAULT 0",
        )
    }
}
