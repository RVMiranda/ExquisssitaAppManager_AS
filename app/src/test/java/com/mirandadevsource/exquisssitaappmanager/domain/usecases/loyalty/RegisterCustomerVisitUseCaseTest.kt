package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegisterCustomerVisitUseCaseTest {
    @Test
    fun `valid qr payload is passed to repository`() = runBlocking {
        val repository = RecordingLoyaltyRepository()
        val useCase = RegisterCustomerVisitUseCase(repository)
        val payload = "a".repeat(64)

        val result = useCase(payload)

        assertEquals(payload, repository.receivedQrPayload)
        assertEquals(false, result.getOrThrow().alreadyRegisteredToday)
    }

    @Test
    fun `invalid qr payload is rejected before network repository call`() = runBlocking {
        val repository = RecordingLoyaltyRepository()
        val result = RegisterCustomerVisitUseCase(repository)("too-short")

        assertNotNull(result.exceptionOrNull())
        assertEquals(null, repository.receivedQrPayload)
    }

    private class RecordingLoyaltyRepository : LoyaltyRepository {
        var receivedQrPayload: String? = null

        override suspend fun getCustomerByQr(qrPayload: String): Result<Customer?> = Result.success(null)
        override suspend fun getCustomerByPhone(phone: String): Result<Customer?> = Result.success(null)
        override suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit> {
            receivedQrPayload = qrPayload
            return Result.success(
                CustomerVisit(
                    customerId = "customer-id",
                    date = "2026-10-09",
                    pointsAwarded = 10,
                    totalVisits = 1,
                    totalLoyaltyPoints = 10,
                    alreadyRegisteredToday = false,
                ),
            )
        }
        override suspend fun getRewards(includeInactive: Boolean): Result<List<LoyaltyReward>> =
            Result.success(emptyList())

        override suspend fun manageReward(
            rewardId: String,
            title: String,
            description: String,
            pointsCost: Int,
            active: Boolean,
            expectedVersion: Long,
        ): Result<LoyaltyReward> = Result.failure(UnsupportedOperationException())

        override suspend fun redeemReward(
            qrPayload: String,
            rewardId: String,
            idempotencyKey: String,
        ): Result<RewardRedemption> = Result.failure(UnsupportedOperationException())
    }
}
