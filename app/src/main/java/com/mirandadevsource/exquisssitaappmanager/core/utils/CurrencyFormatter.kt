package com.mirandadevsource.exquisssitaappmanager.core.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object CurrencyFormatter {
    /**
     * Da formato a un valor en centavos enteros MXN hacia string comercial (ej. 24000L -> "$240.00").
     */
    fun formatCents(cents: Long): String {
        val pesos = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        return String.format(Locale.US, "$%,.2f", pesos)
    }

    /**
     * Parsea un texto numérico en pesos hacia centavos enteros Long.
     */
    fun parsePesosToCents(text: String): Long? {
        val clean = text.replace("$", "").replace(",", "").trim()
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (bd < BigDecimal.ZERO) null else bd.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        } catch (_: Exception) {
            null
        }
    }
}
