package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class EmployeePayment(
    val id: String,
    val empleadoId: String,
    /** Paid amount in integer MXN cents. */
    val montoCents: Long,
    val fechaPago: String?,
    /** Nullable only for legacy rows created before weekly periods were introduced. */
    val periodoInicio: String?,
    val fechaProgramada: String?,
    val notas: String?,
    val registradoPor: String?,
    val createdAt: String,
    val syncVersion: Long = 1L,
    val syncStatus: PaymentSyncStatus = PaymentSyncStatus.CONFIRMED,
    val descuentoPrestamoCents: Long = 0L,
    val prestamoId: String? = null,
) {
    val montoNetoCents: Long get() = maxOf(0L, montoCents - descuentoPrestamoCents)
}

enum class PaymentSyncStatus {
    CONFIRMED,
    PENDING_SYNC,
}
