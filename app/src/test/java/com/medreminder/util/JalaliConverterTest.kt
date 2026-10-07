package com.medreminder.util

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class JalaliConverterTest {

    @Test
    fun nowruz1403() {
        assertEquals(Triple(1403, 1, 1), JalaliConverter.gregorianToJalali(LocalDate(2024, 3, 20)))
        assertEquals(LocalDate(2024, 3, 20), JalaliConverter.jalaliToGregorian(1403, 1, 1))
    }

    @Test
    fun leapDay1403() {
        assertEquals(Triple(1403, 12, 30), JalaliConverter.gregorianToJalali(LocalDate(2025, 3, 20)))
        assertEquals(LocalDate(2025, 3, 20), JalaliConverter.jalaliToGregorian(1403, 12, 30))
    }
}
