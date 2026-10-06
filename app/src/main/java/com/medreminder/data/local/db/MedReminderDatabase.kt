package com.medreminder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medreminder.data.local.entity.DoseLogEntity
import com.medreminder.data.local.entity.MedicationEntity

/**
 * Application database.
 *
 * Schema history:
 *  - v1: initial schema (medications + dose_logs, FKs and indices as declared).
 *  - v2: added `dose_logs.repeat_count` (repeat-reminder counter) — covered by
 *    [MIGRATION_1_2]; the app ships `exportSchema = true`, so every version's
 *    JSON lives in `app/schemas/` and is reviewed in pull requests.
 *
 * Migration strategy: hand-written [androidx.room.Migration] objects for every
 * released version bump (fallbackToDestructiveMigration is intentionally NOT
 * enabled — losing a patient's dose history is unacceptable).
 */
@Database(
    entities = [MedicationEntity::class, DoseLogEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MedReminderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
}
