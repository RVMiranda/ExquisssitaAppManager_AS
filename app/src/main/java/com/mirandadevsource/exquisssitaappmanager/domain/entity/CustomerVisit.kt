package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class CustomerVisit(
    val customerId: String,
    val date: String,
    val pointsAwarded: Int,
    val totalVisits: Int,
    val totalLoyaltyPoints: Int,
    val alreadyRegisteredToday: Boolean,
)
