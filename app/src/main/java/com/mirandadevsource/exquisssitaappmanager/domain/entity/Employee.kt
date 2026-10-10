package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.math.BigDecimal

data class Employee(
    val id: String,
    val userId: String?,
    val nombre: String,
    val apellido: String,
    val telefono: String?,
    /** Weekly wage stored as integer MXN cents. */
    val salarioCents: Long,
    /** ISO weekday: Monday=1 through Sunday=7. */
    val diaPago: Int,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long = 1L,
) {
    val fullName: String get() = "$nombre $apellido".trim()
    /** Exact decimal pesos; do not use binary floating point for money. */
    val salarioPesos: BigDecimal get() = BigDecimal.valueOf(salarioCents, 2)
    val diaPagoNombre: String get() = WEEKDAYS.getOrNull(diaPago - 1) ?: "Día inválido"

    private companion object {
        val WEEKDAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    }
}
