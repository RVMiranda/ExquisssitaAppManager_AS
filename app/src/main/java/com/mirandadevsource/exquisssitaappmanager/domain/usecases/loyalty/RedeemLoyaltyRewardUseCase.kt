package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class RedeemLoyaltyRewardUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(
        qrPayload: String,
        rewardId: String,
        idempotencyKey: String,
    ): Result<RewardRedemption> = loyaltyRepository.redeemReward(
        qrPayload = qrPayload,
        rewardId = rewardId,
        idempotencyKey = idempotencyKey,
    )
}
