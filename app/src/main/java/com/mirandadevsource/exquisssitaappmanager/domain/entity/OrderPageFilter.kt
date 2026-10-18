package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.time.Instant

/** Time bounds are UTC instants supplied by the caller after applying the business timezone. */
data class OrderPageFilter(
    val fromInclusive: Instant? = null,
    val toExclusive: Instant? = null,
    val openOnly: Boolean = false,
) {
    init {
        require(fromInclusive == null || toExclusive == null || fromInclusive < toExclusive) {
            "El inicio del periodo debe ser anterior al final."
        }
    }

    fun scope(): String = "orders:${fromInclusive ?: "all"}:${toExclusive ?: "all"}:${if (openOnly) "open" else "all"}"
}
