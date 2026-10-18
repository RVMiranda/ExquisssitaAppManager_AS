package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class ManageLoyaltyRewardUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(
        rewardId: String,
        title: String,
        description: String,
        pointsCost: Int,
        active: Boolean,
        expectedVersion: Long,
    ): Result<LoyaltyReward> = loyaltyRepository.manageReward(
        rewardId = rewardId,
        title = title,
        description = description,
        pointsCost = pointsCost,
        active = active,
        expectedVersion = expectedVersion,
    )
}
