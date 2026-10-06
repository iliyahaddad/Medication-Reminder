package com.medreminder.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * Abstractions over "now" and the device time zone so that every piece of
 * scheduling logic is pure and testable (inject a fixed [Clock]/[TimeZoneProvider]).
 */

/** Source of the current instant. Production uses [SystemClock]. */
interface Clock {
    /** Current instant in epoch milliseconds. */
    fun nowMillis(): Long

    /** Current instant as a kotlinx-datetime [Instant]. */
    fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis())
}

/** Source of the current default time zone. Production uses [SystemTimeZoneProvider]. */
interface TimeZoneProvider {
    fun current(): TimeZone
}

/** Production clock backed by [System.currentTimeMillis]. */
class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Production time-zone provider backed by [java.util.TimeZone.getDefault]. */
class SystemTimeZoneProvider : TimeZoneProvider {
    /** The JVM default zone id is an IANA id ("Europe/Berlin") which kotlinx-datetime accepts. */
    override fun current(): TimeZone = TimeZone.of(java.util.TimeZone.getDefault().id)
}

/** Convenience conversions between epoch millis and local date/time values. */

/** Interpret these epoch millis as a wall-clock reading in [timeZone]. */
fun Long.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)

/** Encode this wall-clock reading into epoch millis for [timeZone]. */
fun LocalDateTime.toEpochMillis(timeZone: TimeZone): Long =
    toInstant(timeZone).toEpochMilliseconds()

/** Combine this date with a wall-clock time. */
fun LocalDate.at(time: LocalTime): LocalDateTime =
    LocalDateTime(
        year = year,
        month = month,
        dayOfMonth = dayOfMonth,
        time = time,
    )

/** Total minutes-from-midnight of a wall-clock time (seconds ignored). */
fun LocalTime.minutesOfDay(): Int = hour * 60 + minute
