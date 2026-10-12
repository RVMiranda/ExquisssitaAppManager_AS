package com.mirandadevsource.exquisssitaappmanager.domain.entity

/** Monetary assignment and stock consumption are separate values. */
data class PaymentAllocation(
    val orderDetailId: String,
    val quantity: Int,
    val amountCents: Long,
    val stockQuantity: Int = 0,
)

data class PaymentRequest(
    val orderId: String,
    val amountCents: Long,
    val method: PaymentMethod,
    val allocations: List<PaymentAllocation>,
)

data class PaymentCommand(
    val idempotencyKey: String,
    val orderId: String,
    val status: PaymentStatus,
    val transactionId: String? = null,
    val errorCode: String? = null,
)
