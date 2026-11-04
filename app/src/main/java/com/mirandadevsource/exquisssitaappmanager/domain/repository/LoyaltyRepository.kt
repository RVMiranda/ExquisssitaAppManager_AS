package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption

interface LoyaltyRepository {
    /** [qrPayload] is the raw token scanned from the customer's QR. */
    suspend fun getCustomerByQr(qrPayload: String): Result<Customer?>

    suspend fun getCustomerByPhone(phone: String): Result<Customer?>

    /** Records at most one visit per customer and Mexico City business date. */
    suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit>

    suspend fun getRewards(includeInactive: Boolean = false): Result<List<LoyaltyReward>>

    /** Returns reward IDs redeemed by this specific customer inside `[fromIso, toIso)`. */
    suspend fun getCustomerRedeemedRewardIds(
        customerId: String,
        fromIso: String,
        toIso: String,
    ): Result<Set<String>>

    /** Only admin may create or update rewards; the backend validates role and expected version. */
    suspend fun manageReward(
        rewardId: String,
        title: String,
        description: String,
        pointsCost: Int,
        active: Boolean,
        expectedVersion: Long,
    ): Result<LoyaltyReward>

    /** Keep [idempotencyKey] stable when retrying an uncertain request. */
    suspend fun redeemReward(
        qrPayload: String,
        rewardId: String,
        idempotencyKey: String,
    ): Result<RewardRedemption>

}
