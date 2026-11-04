package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.RedeemableReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardPeriod
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class GetCustomerRedeemableRewardsUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(customerId: String, customerPoints: Int): Result<List<RedeemableReward>> =
        invoke(customerId, customerPoints, RewardPeriod.current())

    suspend operator fun invoke(customer: Customer): Result<List<RedeemableReward>> =
        invoke(customer.id, customer.loyaltyPoints)

    /** Period overload keeps the eligibility calculation deterministic for callers and QA. */
    suspend operator fun invoke(
        customerId: String,
        customerPoints: Int,
        period: RewardPeriod,
    ): Result<List<RedeemableReward>> {
        if (customerId.isBlank()) return Result.failure(IllegalArgumentException("Falta el cliente para consultar recompensas."))
        if (customerPoints < 0) return Result.failure(IllegalArgumentException("El saldo de puntos no es válido."))

        val rewards = loyaltyRepository.getRewards(includeInactive = false)
        if (rewards.isFailure) return Result.failure(rewards.exceptionOrNull()!!)
        val activeRewards = rewards.getOrThrow()
        if (activeRewards.isEmpty()) return Result.success(emptyList())
        val redeemed = loyaltyRepository.getCustomerRedeemedRewardIds(
            customerId = customerId,
            fromIso = period.startTimestampIso(),
            toIso = period.endTimestampIso(),
        )
        if (redeemed.isFailure) return Result.failure(redeemed.exceptionOrNull()!!)

        val redeemedIds = redeemed.getOrThrow()
        val eligible = activeRewards.map { reward ->
            val alreadyRedeemed = reward.id in redeemedIds
            val pointsMissing = (reward.pointsCost - customerPoints).coerceAtLeast(0)
            RedeemableReward(
                reward = reward,
                pointsCost = reward.pointsCost,
                isEligible = !alreadyRedeemed && pointsMissing == 0,
                alreadyRedeemedInPeriod = alreadyRedeemed,
                pointsMissing = pointsMissing,
                rejectionReason = when {
                    alreadyRedeemed -> REASON_ALREADY_REDEEMED
                    pointsMissing > 0 -> REASON_INSUFFICIENT_POINTS
                    else -> null
                },
            )
        }.sortedWith(compareBy<RedeemableReward> { !it.isEligible }.thenBy { it.pointsCost }.thenBy { it.reward.title })
        return Result.success(eligible)
    }

    private companion object {
        const val REASON_ALREADY_REDEEMED = "ALREADY_REDEEMED_THIS_PERIOD"
        const val REASON_INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS"
    }
}
