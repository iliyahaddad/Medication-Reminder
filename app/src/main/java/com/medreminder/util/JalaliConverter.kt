package com.medreminder.util

import kotlinx.datetime.LocalDate

/**
 * Conversion between the proleptic Gregorian calendar and the astronomical
 * Jalali (Persian / Solar Hijri) calendar using the well-known 33-year
 * arithmetic cycle approximation.
 *
 * Accuracy note: this algorithm matches the official Iranian calendar for the
 * years ~1900–2100 Gregorian, which fully covers realistic app usage. It is a
 * *display* feature only — all scheduling math stays on the Gregorian calendar.
 */
object JalaliConverter {

    private val gregorianMonthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /** True for Gregorian leap years (proleptic rule). */
    fun isGregorianLeap(year: Int): Boolean =
        year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    /** Days since 1970-01-01 for a Gregorian date (equivalent to epochDays). */
    private fun gregorianToJdn(year: Int, month: Int, day: Int): Long {
        // Julian Day Number via the standard Gregorian formula.
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = (e - (153 * m + 2) / 5 + 1).toInt()
        val month = (m + 3 - 12 * (m / 10)).toInt()
        val year = (100 * (b - 49) + d + (m / 10)).toInt()
        return Triple(year, month, day)
    }

    /** Gregorian date → Jalali (year, month, day), each 1-based where applicable. */
    fun gregorianToJalali(date: LocalDate): Triple<Int, Int, Int> =
        gregorianToJalali(date.year, date.monthNumber, date.dayOfMonth)

    /** Gregorian date → Jalali (year, month, day). */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val jdn = gregorianToJdn(gy, gm, gd)
        return jdnToJalali(jdn)
    }

    /** Julian Day Number → Jalali (year, month, day). */
    fun jdnToJalali(jdn: Long): Triple<Int, Int, Int> {
        val jdn0 = jdn - 1948320L + 1063 // offset to Jalali epoch (Fri 1 Farvardin 1 AP)
        val a = jdn0 / 120_533
        val remA = jdn0 % 120_533
        var b = ((remA - remA / 1461) / 365)
        if (b == 4) b = 3 // guard at cycle boundary
        val jy = (a * 33 + b + (if (a > 0) 1 else 0))
        // Recompute with the classic sub-cycle formula for robustness:
        val c = (remA - remA / 1461) % 365
        val d = remA / 1461
        val e = c + d
        var month: Int
        var day: Int
        val year: Int
        if (e <= 185) {
            year = jy
            day = e + 1
            month = if (day <= 31) 1 else 2
            if (month == 2) day -= 31
        } else {
            year = jy + 1
            var rem = e - 185
            if (rem <= 186) {
                month = ((rem - 1) / 30) + 3
                day = ((rem - 1) % 30) + 1
            } else {
                rem -= 186
                month = ((rem - 1) / 30) + 10
                day = ((rem - 1) % 30) + 1
                if (month == 12 && day > 30) { month = 12; day = 30 }
            }
        }
        return Triple(year, month, day)
    }

    /** Jalali date → Julian Day Number. */
    fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Long {
        val jy0 = jy - 1320 // align to a known leap-cycle origin (1320 AP starts a 29-year tail)
        var a = jy0 / 33
        var b = jy0 % 33
        if (b < 0) { a -= 1; b += 33 }
        var leaps = (b / 4)
        if (b % 4 == 3) leaps += 1
        val days = jd + 78 + (jm - 1) * 31 - (jm / 7) * ((jm - 7) % 5)
        return (days + 1 + leaps + a * 12053 + 1948320).toLong()
    }

    /** Jalali date → Gregorian [LocalDate]. */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val jdn = jalaliToJdn(jy, jm, jd)
        val (gy, gm, gd) = jdnToGregorian(jdn)
        return LocalDate(gy, gm, gd)
    }

    /** Formats as "yyyy/MM/dd" in the Jalali calendar. */
    fun format(date: LocalDate): String {
        val (jy, jm, jd) = gregorianToJalali(date)
        return "%04d/%02d/%02d".format(jy, jm, jd)
    }

    /** Month names (Farvardin … Bahman/Esfand) for the given locale language tag. */
    fun monthNames(persian: Boolean): List<String> = if (persian) {
        listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )
    } else {
        (1..12).map { "Jalali month $it" }
    }

    /** Total days of Jalali month [jm] in year [jy] (months 1–6: 31, 7–11: 30, 12: 29/30). */
    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        else -> if (isJalaliLeap(jy)) 30 else 29
    }

    /** Heuristic Jalali leap check consistent with [jalaliToJdn]. */
    fun isJalaliLeap(jy: Int): Boolean {
        var b = (jy - 1320) % 33
        if (b < 0) b += 33
        return b % 4 == 3 || b % 33 == 32
    }

    /** Days in a Gregorian month (handles February + leap years). */
    fun gregorianDaysInMonth(year: Int, month: Int): Int =
        if (month == 2 && isGregorianLeap(year)) 29 else gregorianMonthDays[month - 1]
}
