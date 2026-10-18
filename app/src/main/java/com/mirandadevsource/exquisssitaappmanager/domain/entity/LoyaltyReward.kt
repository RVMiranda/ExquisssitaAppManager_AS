package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class LoyaltyReward(
    val id: String,
    val title: String,
    val description: String,
    val pointsCost: Int,
    val active: Boolean,
    val syncVersion: Long,
)

data class RewardRedemption(
    val redemptionId: String,
    val customerId: String,
    val rewardId: String,
    val pointsCost: Int,
    val remainingPoints: Int?,
    val alreadyProcessed: Boolean,
)
