package com.medreminder.util

import kotlinx.datetime.DayOfWeek
import java.time.LocalDate as JavaLocalDate

/**
 * Pure helpers for week arithmetic used by the history calendar and the
 * adherence statistics screen.
 *
 * [startOfWeek] mirrors `java.time.temporal.WeekFields.of(locale).firstDayOfWeek`
 * but works on [kotlinx.datetime.DayOfWeek] so callers stay framework-free.
 */
object WeekFields {

    /**
     * Returns the first day (Monday or Sunday, depending on [firstDay]) of the
     * week that contains [date]. Month ends, year boundaries and leap years are
     * handled by delegating to [JavaLocalDate], which is fully proleptic-Gregorian.
     */
    fun startOfWeek(date: kotlinx.datetime.LocalDate, firstDay: DayOfWeek): kotlinx.datetime.LocalDate {
        val jd = JavaLocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        val offset = (jd.dayOfWeek.value - firstDay.isoDayNumber + 7) % 7
        val start = jd.minusDays(offset.toLong())
        return kotlinx.datetime.LocalDate(start.year, start.monthValue, start.dayOfMonth)
    }

    /** ISO-8601 day-of-week number (Monday = 1 … Sunday = 7). */
    val DayOfWeek.isoDayNumber: Int
        get() = when (this) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }
}
