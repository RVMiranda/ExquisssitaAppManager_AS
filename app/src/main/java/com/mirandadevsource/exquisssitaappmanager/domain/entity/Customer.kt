package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val totalVisits: Int,
    val loyaltyPoints: Int,
    val registeredAt: String?,
)

/** Carries the QR payload only in the response to customer creation. */
data class CreatedCustomer(
    val customer: Customer,
    val qrPayload: String,
)
