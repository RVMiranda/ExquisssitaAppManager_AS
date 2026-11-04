package com.mirandadevsource.exquisssitaappmanager.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {
    val MEXICO_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("es-MX"))
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a", Locale.forLanguageTag("es-MX"))
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-MX"))

    fun toMexicoZonedDateTime(isoString: String?): ZonedDateTime? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString).atZone(MEXICO_ZONE)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).atZoneSameInstant(MEXICO_ZONE)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toMexicoLocalDate(isoString: String?): LocalDate? {
        return toMexicoZonedDateTime(isoString)?.toLocalDate()
    }

    fun isTodayInMexico(isoString: String?): Boolean {
        val orderDate = toMexicoLocalDate(isoString) ?: return false
        val today = LocalDate.now(MEXICO_ZONE)
        return orderDate == today
    }

    fun formatOrderTime(isoString: String?): String {
        val zdt = toMexicoZonedDateTime(isoString) ?: return isoString.orEmpty().take(16).replace("T", " ")
        return zdt.format(TIME_FORMATTER)
    }

    fun formatOrderDateTime(isoString: String?): String {
        val zdt = toMexicoZonedDateTime(isoString) ?: return isoString.orEmpty().take(16).replace("T", " ")
        return zdt.format(DATE_TIME_FORMATTER)
    }

    fun formatOrderDate(isoString: String?): String {
        val zdt = toMexicoZonedDateTime(isoString) ?: return isoString.orEmpty().take(10)
        return zdt.format(DATE_FORMATTER)
    }

    fun currentMexicoLocalDate(): LocalDate = LocalDate.now(MEXICO_ZONE)

    fun nowMexicoCity(): ZonedDateTime = ZonedDateTime.now(MEXICO_ZONE)
}
