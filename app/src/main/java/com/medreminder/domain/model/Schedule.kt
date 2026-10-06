package com.medreminder.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Physical form of a medication. Stored in Room as its [key] string so that
 * renaming an entry never breaks stored data.
 */
@Serializable
enum class MedicationForm(val key: String) {
    TABLET("tablet"),
    CAPSULE("capsule"),
    LIQUID("liquid"),
    INJECTION("injection"),
    DROPS("drops"),
    INHALER("inhaler"),
    OINTMENT("ointment"),
    SUPPOSITORY("suppository"),
    OTHER("other"),
    ;

    companion object {
        /** Parse a persisted/serialized key, falling back to [OTHER]. */
        fun fromKey(key: String): MedicationForm =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER
    }
}

/** Units accepted for the dosage value (kept intentionally small & validated). */
@Serializable
enum class DosageUnit(val key: String) {
    MG("mg"),
    G("g"),
    ML("ml"),
    MCG("mcg"),
    IU("IU"),
    UNIT("unit"),
    DROP("drop"),
    PUFF("puff"),
    TABLET("tablet"),
    CAPSULE("capsule"),
    VIAL("vial"),
    APPLICATION("application"),
    ;

    companion object {
        fun fromKey(key: String): DosageUnit? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/**
 * Sealed hierarchy of all supported dosing schedules.
 *
 * TIME POLICY ("floating local time"): every schedule is defined against the
 * device's *wall clock*. 08:30 means "08:30 wherever the user currently is".
 * When the user travels across time zones or DST shifts, alarms keep firing at
 * the same local wall-clock time; absolute instants change. This matches how
 * people experience daily medication and is documented in README/PRIVACY.
 */
@Serializable
sealed interface ScheduleType {

    /** Repeat at the same set of times every day. */
    @Serializable
    data class Daily(
        val times: List<LocalTime>,
    ) : ScheduleType

    /** Repeat at [times] only on the given weekdays. */
    @Serializable
    data class Weekly(
        val days: Set<DayOfWeek>,
        val times: List<LocalTime>,
    ) : ScheduleType

    /** Every [intervalDays] days starting at [startDate] [startTime] (anchored). */
    @Serializable
    data class EveryNDays(
        val startDate: LocalDate,
        val startTime: LocalTime,
        val intervalDays: Int,
    ) : ScheduleType

    /** Every [intervalHours] hours starting at [anchorEpochMillis]. */
    @Serializable
    data class EveryNHours(
        val anchorEpochMillis: Long,
        val intervalHours: Int,
    ) : ScheduleType

    /** Cyclic therapy: [daysOn] days on, then [daysOff] days off, repeating. */
    @Serializable
    data class Cyclic(
        val startDate: LocalDate,
        val times: List<LocalTime>,
        val daysOn: Int,
        val daysOff: Int,
    ) : ScheduleType

    /** User-triggered only; never auto-scheduled, but can be logged manually. */
    @Serializable
    data object AsNeeded : ScheduleType
}
