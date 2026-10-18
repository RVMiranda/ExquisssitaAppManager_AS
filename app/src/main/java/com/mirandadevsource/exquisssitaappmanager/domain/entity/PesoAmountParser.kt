package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.math.BigDecimal
import java.math.RoundingMode

/** Parses a peso amount without floating point or silent cent truncation. */
object PesoAmountParser {
    private val acceptedFormat = Regex("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?")

    /** Returns null for ambiguous, negative, malformed, over-precision, or overflowing values. */
    fun toCents(input: String): Long? {
        val value = input.trim().removePrefix("\$").trim()
        if (!acceptedFormat.matches(value)) return null
        return try {
            BigDecimal(value.replace(",", ""))
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
                .takeIf { it >= 0L }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }
}
