package com.medreminder.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.medreminder.util.at
import java.util.zip.CRC32

/**
 * Pure schedule calculation engine — no Android, no DB, fully unit-testable.
 *
 * Design decisions (see README "Reliability"):
 *  - **Floating local time**: occurrences are computed against the device wall
 *    clock; crossing time zones / DST keeps the same local times. The only
 *    exception is [ScheduleType.EveryNHours], which is anchored to absolute
 *    instants by definition.
 *  - **DST-safe iteration**: we enumerate candidate days as calendar dates and
 *    convert each wall-clock [LocalDateTime] to an instant via kotlinx-datetime,
 *    which resolves gaps/overlaps deterministically. We never add fixed
 *    millisecond offsets to wall-clock times.
 *  - Occurrences at or before `fromMillis` are never returned (never schedule
 *    in the past).
 */
object ScheduleCalculator {

    /** Days scanned per scheduling pass (safety bound for sparse schedules). */
    const val MAX_HORIZON_DAYS = 400

    /** Look-ahead window used when asking only for the *next* occurrence (~45 days). */
    const val LOOKAHEAD_MILLIS = 45L * 24 * 3_600_000L

    /**
     * Deterministic positive id for a dose occurrence, derived from
     * (medicationId, scheduledAtMillis). Used as the Room primary key and the
     * AlarmManager/PendingIntent request code → scheduling twice is idempotent.
     */
    fun doseId(medicationId: Long, scheduledAtMillis: Long): Long {
        val crc = CRC32()
        crc.update("$medicationId|$scheduledAtMillis".toByteArray())
        return crc.value // 0..2^32-1; also safe as an Int PendingIntent requestCode
    }

    /**
     * All occurrences of [schedule] valid between [validityStart] and
     * [validityEndExclusive] (dates), returned as epoch millis within
     * `(fromMillis, untilMillis]`.
     */
    fun occurrences(
        schedule: ScheduleType,
        validityStart: LocalDate,
        validityEndExclusive: LocalDate?,
        fromMillis: Long,
        untilMillis: Long,
        timeZone: TimeZone,
    ): List<Long> {
        if (untilMillis <= fromMillis) return emptyList()
        if (schedule is ScheduleType.AsNeeded) return emptyList()

        val fromDate = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(timeZone).date
        val firstDay = maxOf(fromDate, validityStart)

        val result = mutableListOf<Long>()
        var day = firstDay
        var cursor = 0
        while (cursor < MAX_HORIZON_DAYS) {
            if (validityEndExclusive != null && day >= validityEndExclusive) break
            for (slot in slotsOn(schedule, day, timeZone)) {
                val millis = slot.toEpochMillisSafe(timeZone) ?: continue
                if (millis > fromMillis && millis <= untilMillis) result += millis
            }
            day = day.nextDay()
            cursor++
        }
        return result.distinct().sorted()
    }

    /** Next single occurrence strictly after [fromMillis], or null if none exists. */
    fun nextOccurrence(
        schedule: ScheduleType,
        validityStart: LocalDate,
        validityEndExclusive: LocalDate?,
        fromMillis: Long,
        timeZone: TimeZone,
    ): Long? = occurrences(
        schedule = schedule,
        validityStart = validityStart,
        validityEndExclusive = validityEndExclusive,
        fromMillis = fromMillis,
        untilMillis = fromMillis + LOOKAHEAD_MILLIS,
        timeZone = timeZone,
    ).firstOrNull()

    /** True when at least one future occurrence exists after [fromMillis]. */
    fun hasFutureOccurrences(
        schedule: ScheduleType,
        validityStart: LocalDate,
        validityEndExclusive: LocalDate?,
        fromMillis: Long,
        timeZone: TimeZone,
    ): Boolean = nextOccurrence(
        schedule, validityStart, validityEndExclusive, fromMillis, timeZone,
    ) != null

    /**
     * Wall-clock date-times produced by [schedule] on calendar [day].
     * Empty list when the schedule does not fire that day.
     */
    fun slotsOn(
        schedule: ScheduleType,
        day: LocalDate,
        timeZone: TimeZone,
    ): List<LocalDateTime> = when (schedule) {
        is ScheduleType.Daily -> schedule.times.map { day.at(it) }

        is ScheduleType.Weekly ->
            if (day.dayOfWeek in schedule.days) schedule.times.map { day.at(it) } else emptyList()

        is ScheduleType.EveryNDays -> {
            val diff = day.toEpochDays() - schedule.startDate.toEpochDays()
            if (diff >= 0 && schedule.intervalDays > 0 && diff % schedule.intervalDays == 0) {
                listOf(day.at(schedule.startTime))
            } else {
                emptyList()
            }
        }

        is ScheduleType.EveryNHours -> {
            // Anchored on absolute instants: walk whole intervals from the
            // anchor and keep those whose instant lands on this calendar day.
            val intervalMillis = schedule.intervalHours.coerceAtLeast(1) * 3_600_000L
            val dayStart = day.at(LocalTime.MIN).toEpochMillisSafe(timeZone) ?: return emptyList()
            val dayEnd = day.at(LocalTime.MAX).toEpochMillisSafe(timeZone) ?: return emptyList()
            if (schedule.anchorEpochMillis > dayEnd) return emptyList()

            // Jump directly to the first interval that can land on this day.
            // Without this, an anchor from years ago would force every day to
            // iterate through thousands of elapsed intervals.
            val first = if (schedule.anchorEpochMillis >= dayStart) {
                schedule.anchorEpochMillis
            } else {
                val distance = dayStart - schedule.anchorEpochMillis
                val stepsForward = (distance + intervalMillis - 1) / intervalMillis
                schedule.anchorEpochMillis + stepsForward * intervalMillis
            }

            val out = mutableListOf<LocalDateTime>()
            var t = first
            while (t <= dayEnd) {
                out += Instant.fromEpochMilliseconds(t).toLocalDateTime(timeZone)
                t += intervalMillis
            }
            out
        }

        is ScheduleType.Cyclic -> {
            val diff = day.toEpochDays() - schedule.startDate.toEpochDays()
            if (diff < 0) return emptyList()
            val cycleLength = schedule.daysOn + schedule.daysOff
            if (cycleLength <= 0) return emptyList()
            val posInCycle = (diff % cycleLength).toInt()
            if (posInCycle < schedule.daysOn) schedule.times.map { day.at(it) } else emptyList()
        }

        ScheduleType.AsNeeded -> emptyList()
    }
}

/** Encode a wall-clock datetime to epoch millis; defensive against exotic zone rules. */
internal fun LocalDateTime.toEpochMillisSafe(timeZone: TimeZone): Long? =
    runCatching { toInstant(timeZone).toEpochMilliseconds() }.getOrNull()

/** Next calendar day — handles month ends, year ends and leap years. */
internal fun LocalDate.nextDay(): LocalDate = plus(kotlinx.datetime.DatePeriod(days = 1))
