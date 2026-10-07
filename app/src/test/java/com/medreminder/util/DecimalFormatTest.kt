package com.medreminder.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DecimalFormatTest {
    @Test fun integralValuesHaveNoDecimalPoint() = assertEquals("5", formatDecimal(5.0))
    @Test fun fractionsKeepSignificantDigits() = assertEquals("0.25", formatDecimal(0.25))
    @Test fun trailingZerosAreStripped() = assertEquals("2.5", formatDecimal(2.50))
}
