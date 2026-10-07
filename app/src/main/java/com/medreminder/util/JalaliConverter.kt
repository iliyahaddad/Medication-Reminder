package com.medreminder.util

import kotlinx.datetime.LocalDate

/**
 * Gregorian ↔ Jalali (Solar Hijri) conversion using the well-tested Jalaali
 * break-year algorithm. The scheduling engine itself remains Gregorian and
 * uses this converter only for presentation/input.
 *
 * Supported Jalali range: -61..3177 (the algorithm's documented range).
 */
object JalaliConverter {

    private val gregorianMonthDays =
        intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private data class JalCal(val leap: Int, val gy: Int, val march: Int)

    fun isGregorianLeap(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    fun gregorianToJalali(date: LocalDate): Triple<Int, Int, Int> =
        gregorianToJalali(date.year, date.monthNumber, date.dayOfMonth)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        require(gm in 1..12) { "Gregorian month out of range" }
        require(gd in 1..gregorianDaysInMonth(gy, gm)) { "Gregorian day out of range" }

        val gregorian = java.time.LocalDate.of(gy, gm, gd)
        var jy = gy - 621
        var start = jalaliYearStart(jy)
        if (gregorian.isBefore(start)) {
            jy--
            start = jalaliYearStart(jy)
        }

        val dayOfYear = java.time.temporal.ChronoUnit.DAYS.between(start, gregorian).toInt()
        return if (dayOfYear < 186) {
            Triple(jy, dayOfYear / 31 + 1, dayOfYear % 31 + 1)
        } else {
            val remaining = dayOfYear - 186
            Triple(jy, remaining / 30 + 7, remaining % 30 + 1)
        }
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        require(jy in breaks.first() until breaks.last()) { "Jalali year out of supported range" }
        require(jm in 1..12) { "Jalali month out of range" }
        require(jd in 1..daysInMonth(jy, jm)) { "Jalali day out of range" }

        val offset = when {
            jm <= 6 -> (jm - 1) * 31 + (jd - 1)
            else -> 186 + (jm - 7) * 30 + (jd - 1)
        }
        val gregorian = jalaliYearStart(jy).plusDays(offset.toLong())
        return LocalDate(gregorian.year, gregorian.monthValue, gregorian.dayOfMonth)
    }

    fun format(date: LocalDate): String {
        val (jy, jm, jd) = gregorianToJalali(date)
        return "%04d/%02d/%02d".format(jy, jm, jd)
    }

    /** English transliterations used by the UI; no localized text is embedded in the app. */
    fun monthNames(): List<String> = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    )

    fun daysInMonth(jy: Int, jm: Int): Int {
        require(jm in 1..12) { "Jalali month out of range" }
        return when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            isJalaliLeap(jy) -> 30
            else -> 29
        }
    }

    fun isJalaliLeap(jy: Int): Boolean = jalCal(jy).leap == 0

    fun gregorianDaysInMonth(year: Int, month: Int): Int =
        if (month == 2 && isGregorianLeap(year)) 29 else gregorianMonthDays[month - 1]

    private fun jalCal(jy: Int): JalCal {
        require(jy in breaks.first() until breaks.last()) { "Jalali year out of supported range" }

        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0

        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }

        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ++

        val leapG = gy / 4 - (((gy / 100) + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) {
            n = n - jump + ((jump + 4) / 33) * 33
        }
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4

        return JalCal(leap, gy, march)
    }

    private fun jalaliYearStart(jy: Int): java.time.LocalDate {
        val cal = jalCal(jy)
        return java.time.LocalDate.of(cal.gy, 3, cal.march)
    }
}
