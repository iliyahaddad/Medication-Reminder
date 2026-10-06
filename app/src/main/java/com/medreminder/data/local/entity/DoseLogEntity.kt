package com.medreminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one dose occurrence.
 *
 * The primary key is **not** auto-generated: it is the deterministic id
 * computed by `ScheduleCalculator.doseId(medicationId, scheduledAtMillis)`,
 * which makes re-scheduling idempotent (INSERT OR IGNORE on conflict).
 */
@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("medication_id"),
        Index("scheduled_at"),
        Index(value = ["medication_id", "scheduled_at"], unique = true),
        Index("status"),
    ],
)
data class DoseLogEntity(
    @PrimaryKey
    val id: Long,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAtMillis: Long,
    /** One of the keys of [com.medreminder.domain.model.DoseStatus]. */
    val status: String,
    @ColumnInfo(name = "taken_at") val takenAtMillis: Long?,
    @ColumnInfo(name = "skipped_at") val skippedAtMillis: Long?,
    @ColumnInfo(name = "remind_until") val remindUntilMillis: Long?,
    /** Number of repeat reminders already raised for this dose. */
    @ColumnInfo(name = "repeat_count") val repeatCount: Int = 0,
)
