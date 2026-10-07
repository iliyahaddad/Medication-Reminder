package com.medreminder.domain.model

import com.medreminder.util.at
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toEpochMilliseconds
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalculatorTest {

    private val zone = TimeZone.UTC

    @Test
    fun dailyScheduleReturnsOnlyFutureOccurrences() {
        val start = LocalDate(2026, 10, 1)
        val now = LocalDate(2026, 10, 6).at(LocalTime(9, 0)).toInstant(zone).toEpochMilliseconds()
        val until = LocalDate(2026, 10, 7).at(LocalTime(23, 59)).toInstant(zone).toEpochMilliseconds()

        val result = ScheduleCalculator.occurrences(
            schedule = ScheduleType.Daily(listOf(LocalTime(8, 0), LocalTime(10, 0))),
            validityStart = start,
            validityEndExclusive = null,
            fromMillis = now,
            untilMillis = until,
            timeZone = zone,
        )

        assertEquals(3, result.size)
        assertTrue(result.all { it > now })
    }

    @Test
    fun inclusiveMedicationEndDateCanBeRepresentedWithExclusiveBoundary() {
        val now = LocalDate(2026, 10, 6).at(LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val until = LocalDate(2026, 10, 8).at(LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()

        val result = ScheduleCalculator.occurrences(
            schedule = ScheduleType.Daily(listOf(LocalTime(12, 0))),
            validityStart = LocalDate(2026, 10, 6),
            validityEndExclusive = LocalDate(2026, 10, 8),
            fromMillis = now,
            untilMillis = until,
            timeZone = zone,
        )

        assertEquals(2, result.size)
    }

    @Test
    fun deterministicDoseIdIsStable() {
        val a = ScheduleCalculator.doseId(42L, 1_800_000_000_000L)
        val b = ScheduleCalculator.doseId(42L, 1_800_000_000_000L)
        val c = ScheduleCalculator.doseId(42L, 1_800_000_060_000L)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
