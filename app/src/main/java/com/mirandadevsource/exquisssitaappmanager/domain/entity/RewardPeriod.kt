package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Calendar-semester window used by the loyalty reward eligibility rule. */
data class RewardPeriod private constructor(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    fun startTimestampIso(): String = startInclusive.atStartOfDay(BUSINESS_ZONE).toInstant().toString()

    fun endTimestampIso(): String = endExclusive.atStartOfDay(BUSINESS_ZONE).toInstant().toString()

    fun displayLabel(): String =
        "Periodo ${if (startInclusive.monthValue == 1) "Ene - Jun" else "Jul - Dic"} ${startInclusive.year}"

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")

        fun current(): RewardPeriod = forInstant(Instant.now())

        fun forInstant(instant: Instant): RewardPeriod {
            val date = instant.atZone(BUSINESS_ZONE).toLocalDate()
            val start = if (date.monthValue <= 6) LocalDate.of(date.year, 1, 1)
            else LocalDate.of(date.year, 7, 1)
            return RewardPeriod(start, start.plusMonths(6))
        }
    }
}
