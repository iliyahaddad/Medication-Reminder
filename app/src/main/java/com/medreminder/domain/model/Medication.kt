package com.medreminder.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Lifecycle state of a single scheduled dose occurrence. */
@Serializable
enum class DoseStatus(val key: String) {
    SCHEDULED("scheduled"),
    TAKEN("taken"),
    SKIPPED("skipped"),
    MISSED("missed"),
    ;

    companion object {
        fun fromKey(key: String): DoseStatus =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SCHEDULED
    }
}

/** Whether a medication currently produces alarms. */
@Serializable
enum class MedicationStatus(val key: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    ENDED("ended"),
    ;

    companion object {
        fun fromKey(key: String): MedicationStatus =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ACTIVE
    }
}

/**
 * A medication as stored & transported (domain model — no Room/Compose types).
 *
 * @property stockCount remaining units, or null when the user does not track stock.
 * @property refillThreshold low-stock alert threshold (only meaningful when [stockCount] != null).
 */
@Serializable
data class Medication(
    val id: Long = 0L,
    val name: String,
    val dosageValue: Double,
    val dosageUnit: DosageUnit,
    val form: MedicationForm,
    val notes: String = "",
    /** ARGB color int chosen by the user for chips/icons. */
    val colorArgb: Int = 0xFF6750A4.toInt(),
    /** Stable icon key resolved to a Material icon in the UI layer. */
    val iconKey: String = MedicationIcon.PILL.key,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: MedicationStatus = MedicationStatus.ACTIVE,
    val stockCount: Int? = null,
    val refillThreshold: Int = 0,
    val schedule: ScheduleType,
    /** Full-screen alarm style instead of a heads-up notification. */
    val alarmStyle: Boolean = false,
) {
    /** True when the medication is past its end date (inclusive of that day). */
    fun isExpired(today: LocalDate): Boolean = endDate?.let { today > it } == true

    /** True when stock tracking is enabled and at/below the refill threshold. */
    fun isLowStock(): Boolean =
        stockCount != null && stockCount <= refillThreshold && stockCount >= 0
}

/** Icon keys selectable in the UI; mapped to Material icons in the presentation layer. */
@Serializable
enum class MedicationIcon(val key: String) {
    PILL("pill"),
    CAPSULE("capsule"),
    SYRUP("syrup"),
    INJECTION("injection"),
    DROPS("drops"),
    INHALER("inhaler"),
    CREAM("cream"),
    DEVICE("device"),
    ;

    companion object {
        fun fromKey(key: String): MedicationIcon =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PILL
    }
}

/**
 * One concrete dose occurrence of a medication.
 *
 * Uniqueness (idempotency) is guaranteed by the pair (medicationId, scheduledAtMillis):
 * the DB has a unique index on it and the scheduler computes deterministic ids
 * from those two values, so re-running scheduling never creates duplicates.
 */
@Serializable
data class DoseLog(
    val id: Long = 0L,
    val medicationId: Long,
    /** Wall-clock instant (epoch millis) this dose was due. */
    val scheduledAtMillis: Long,
    val status: DoseStatus = DoseStatus.SCHEDULED,
    val takenAtMillis: Long? = null,
    val skippedAtMillis: Long? = null,
    /** When the next repeat-reminder should fire (null = no reminder pending). */
    val remindUntilMillis: Long? = null,
    /** Number of repeat reminders already delivered for this dose. */
    val repeatCount: Int = 0,
)

/** Aggregate adherence statistic over a period. */
data class AdherenceStats(
    val totalScheduled: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
) {
    /** Percentage of doses taken out of all resolved (non-pending) doses, 0..100. */
    val adherencePercent: Int
        get() {
            val resolved = taken + skipped + missed
            return if (resolved == 0) 100 else (taken * 100 / resolved)
        }
}
