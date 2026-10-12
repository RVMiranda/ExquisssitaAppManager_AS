package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class PaymentLineStatus(val databaseValue: String) {
    PENDING("Pendiente"), PAID("Pagado"), CANCELLED("Cancelado");

    companion object {
        fun fromDatabase(value: String): PaymentLineStatus =
            entries.firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class OrderDetail(
    val id: String,
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val paymentStatus: PaymentLineStatus,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val isPendingSync: Boolean = false,
) {
    val lineTotalCents: Long get() = quantity.toLong() * unitPriceCents
}
