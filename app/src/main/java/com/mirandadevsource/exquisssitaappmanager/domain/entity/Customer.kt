package com.mirandadevsource.exquisssitaappmanager.domain.entity

import java.time.Instant

data class Customer(
    val id: String,
    val name: String,
    val phone: String?,
    val totalVisits: Int,
    val loyaltyPoints: Int,
    val registeredAt: String?,
    val fetchedAt: Instant? = null,
    val isCached: Boolean = false,
)
