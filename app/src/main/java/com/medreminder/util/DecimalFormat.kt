package com.medreminder.util

/** Formats 5.0 as "5" and 0.25 as "0.25" (locale-independent, no trailing zeros). */
fun formatDecimal(value: Double): String =
    if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        value.toBigDecimal().stripTrailingZeros().toPlainString()
    }
