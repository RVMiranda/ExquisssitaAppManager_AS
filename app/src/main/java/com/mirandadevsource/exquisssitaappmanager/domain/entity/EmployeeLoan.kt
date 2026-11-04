package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.math.BigDecimal

enum class LoanStatus(val databaseValue: String, val label: String) {
    PENDIENTE("pendiente", "Pendiente"),
    PAGO_PARCIAL("pago_parcial", "Pago Parcial"),
    LIQUIDADO("liquidado", "Liquidado"),
    CANCELADO("cancelado", "Cancelado");

    companion object {
        fun fromDatabase(raw: String?): LoanStatus = when (raw?.trim()?.lowercase()) {
            "pago_parcial" -> PAGO_PARCIAL
            "liquidado" -> LIQUIDADO
            "cancelado" -> CANCELADO
            else -> PENDIENTE
        }
    }
}

data class EmployeeLoan(
    val id: String,
    val empleadoId: String,
    val empleadoNombre: String? = null,
    val montoCentavos: Long,
    val saldoPendienteCentavos: Long,
    val fechaPrestamo: String,
    val notas: String?,
    val estado: LoanStatus,
    val registradoPor: String?,
    val createdAt: String,
    val updatedAt: String,
) {
    val montoPesos: BigDecimal get() = BigDecimal.valueOf(montoCentavos, 2)
    val saldoPendientePesos: BigDecimal get() = BigDecimal.valueOf(saldoPendienteCentavos, 2)
    val totalAbonadoCentavos: Long get() = maxOf(0L, montoCentavos - saldoPendienteCentavos)
    val totalAbonadoPesos: BigDecimal get() = BigDecimal.valueOf(totalAbonadoCentavos, 2)
    val isPending: Boolean get() = estado == LoanStatus.PENDIENTE || estado == LoanStatus.PAGO_PARCIAL
}

data class LoanPayment(
    val id: String,
    val prestamoId: String,
    val montoCentavos: Long,
    val saldoAnteriorCentavos: Long,
    val saldoRestanteCentavos: Long,
    val fechaAbono: String,
    val tipo: String, // 'deduccion_nomina', 'pago_directo', 'ajuste'
    val nominaPagoId: String?,
    val notas: String?,
    val registradoPor: String?,
    val createdAt: String,
) {
    val montoPesos: BigDecimal get() = BigDecimal.valueOf(montoCentavos, 2)
}
