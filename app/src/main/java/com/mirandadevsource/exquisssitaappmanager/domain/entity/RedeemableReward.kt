package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class RedeemableReward(
    val reward: LoyaltyReward,
    val pointsCost: Int,
    val isEligible: Boolean,
    val alreadyRedeemedInPeriod: Boolean,
    val pointsMissing: Int,
    /** Stable domain reason code for UI mapping; null when the reward can be redeemed. */
    val rejectionReason: String?,
)
