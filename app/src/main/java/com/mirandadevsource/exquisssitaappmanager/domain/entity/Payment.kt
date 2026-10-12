package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class PaymentMethod(val databaseValue: String) {
    CASH("Efectivo"), CARD("Tarjeta"), TRANSFER("Transferencia"), OTHER("Otro");

    companion object {
        fun fromDatabase(value: String): PaymentMethod =
            entries.firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class PaymentStatus { PENDING_SYNC, CONFIRMED, REJECTED }

data class Payment(
    val id: String,
    val orderId: String,
    val amountCents: Long,
    val method: PaymentMethod,
    val paidAt: String,
    val idempotencyKey: String,
    val status: PaymentStatus,
    val allocations: List<PaymentAllocation> = emptyList(),
    val errorCode: String? = null,
)
