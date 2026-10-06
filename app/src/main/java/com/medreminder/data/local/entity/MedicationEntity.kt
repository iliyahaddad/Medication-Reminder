package com.medreminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a medication.
 *
 * The schedule is stored as its serialized JSON ([scheduleJson]) because the
 * sealed hierarchy has no fixed relational shape; kotlinx-serialization gives a
 * stable, version-tolerant representation that backup/restore reuses verbatim.
 */
@Entity(
    tableName = "medications",
    indices = [
        Index("name"),
        Index("status"),
    ],
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "dosage_value") val dosageValue: Double,
    /** Key of [DosageUnit]. */
    @ColumnInfo(name = "dosage_unit") val dosageUnit: String,
    /** Key of [MedicationForm]. */
    val form: String,
    val notes: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    /** ISO-8601 date (yyyy-MM-dd). */
    @ColumnInfo(name = "start_date") val startDate: String,
    /** ISO-8601 date or null when open-ended. */
    @ColumnInfo(name = "end_date") val endDate: String?,
    /** Key of [MedicationStatus]. */
    val status: String,
    /** Remaining units, or null when stock tracking is off. */
    @ColumnInfo(name = "stock_count") val stockCount: Int?,
    @ColumnInfo(name = "refill_threshold") val refillThreshold: Int,
    /** Serialized [com.medreminder.domain.model.ScheduleType]. */
    @ColumnInfo(name = "schedule_json") val scheduleJson: String,
    @ColumnInfo(name = "alarm_style") val alarmStyle: Boolean,
)
