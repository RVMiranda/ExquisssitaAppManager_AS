package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardPeriod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.GetCustomerRedeemableRewardsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.RedeemLoyaltyRewardUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LoyaltyRedemptionFlowTest {

    private val customer = Customer(
        id = "cust-1",
        name = "Cliente Prueba",
        phone = "9611234567",
        totalVisits = 10,
        loyaltyPoints = 100,
        registeredAt = "2026-10-01T12:00:00Z",
        fetchedAt = Instant.now(),
        isCached = false,
    )

    private val reward1 = LoyaltyReward(
        id = "rew-1",
        title = "Postre Gratis",
        description = "Un delicioso postre",
        pointsCost = 40,
        active = true,
        syncVersion = 1L,
    )

    private val reward2 = LoyaltyReward(
        id = "rew-2",
        title = "Pastel Completo",
        description = "Pastel mediano 8 porciones",
        pointsCost = 150, // Faltan 50 puntos
        active = true,
        syncVersion = 1L,
    )

    private val reward3 = LoyaltyReward(
        id = "rew-3",
        title = "Café Americano",
        description = "Taza de 12oz",
        pointsCost = 25,
        active = true,
        syncVersion = 1L,
    )

    @Test
    fun `GetCustomerRedeemableRewardsUseCase identifies eligible rewards and sorts by points`() = runBlocking {
        val fakeRepo = FakeLoyaltyRepo(
            rewards = listOf(reward1, reward2, reward3),
            redeemedRewardIds = emptySet(),
        )
        val useCase = GetCustomerRedeemableRewardsUseCase(fakeRepo)

        val result = useCase(customer)
        assertTrue(result.isSuccess)

        val list = result.getOrThrow()
        assertEquals(3, list.size)

        // Ambos reward1 y reward3 son elegibles (costo 40 y 25 <= 100).
        // reward3 (25 pts) va primero por pointsCost asc.
        val first = list[0]
        assertEquals("rew-3", first.reward.id)
        assertTrue(first.isEligible)
        assertEquals(0, first.pointsMissing)
        assertFalse(first.alreadyRedeemedInPeriod)

        val second = list[1]
        assertEquals("rew-1", second.reward.id)
        assertTrue(second.isEligible)
        assertEquals(0, second.pointsMissing)

        // reward2 cuesta 150 > 100 saldo, no es elegible y va al final
        val third = list[2]
        assertEquals("rew-2", third.reward.id)
        assertFalse(third.isEligible)
        assertEquals(50, third.pointsMissing)
    }

    @Test
    fun `GetCustomerRedeemableRewardsUseCase marks rewards already redeemed this semester as ineligible`() = runBlocking {
        val fakeRepo = FakeLoyaltyRepo(
            rewards = listOf(reward1, reward3),
            redeemedRewardIds = setOf("rew-1"), // reward1 ya canjeada este semestre
        )
        val useCase = GetCustomerRedeemableRewardsUseCase(fakeRepo)

        val result = useCase(customer)
        assertTrue(result.isSuccess)

        val list = result.getOrThrow()
        val item1 = list.first { it.reward.id == "rew-1" }
        assertFalse(item1.isEligible)
        assertTrue(item1.alreadyRedeemedInPeriod)
        assertEquals("ALREADY_REDEEMED_THIS_PERIOD", item1.rejectionReason)

        val item3 = list.first { it.reward.id == "rew-3" }
        assertTrue(item3.isEligible)
        assertFalse(item3.alreadyRedeemedInPeriod)
        assertNull(item3.rejectionReason)
    }

    @Test
    fun `RedeemLoyaltyRewardUseCase successfully redeems and returns updated balance`() = runBlocking {
        val fakeRepo = FakeLoyaltyRepo(
            rewards = listOf(reward1),
        )
        val useCase = RedeemLoyaltyRewardUseCase(fakeRepo)

        val qrToken = "x".repeat(64)
        val result = useCase(qrToken, "rew-1", "idempotency-key-123")
        assertTrue(result.isSuccess)

        val redemption = result.getOrThrow()
        assertEquals("red-1", redemption.redemptionId)
        assertEquals("rew-1", redemption.rewardId)
        assertEquals(40, redemption.pointsCost)
        assertEquals(60, redemption.remainingPoints) // 100 - 40 = 60
    }

    @Test
    fun `RedeemLoyaltyRewardUseCase propagates backend error when already redeemed in semester`() = runBlocking {
        val fakeRepo = FakeLoyaltyRepo(
            rewards = listOf(reward1),
            redemptionError = "reward_already_redeemed_this_period",
        )
        val useCase = RedeemLoyaltyRewardUseCase(fakeRepo)

        val qrToken = "y".repeat(64)
        val result = useCase(qrToken, "rew-1", "idempotency-key-456")
        assertTrue(result.isFailure)
        assertEquals("reward_already_redeemed_this_period", result.exceptionOrNull()?.message)
    }

    @Test
    fun `RewardPeriod correctly calculates CDMX semester boundaries and display label`() {
        val period = RewardPeriod.current()
        assertNotNull(period.startTimestampIso())
        assertNotNull(period.endTimestampIso())
        val label = period.displayLabel()
        assertTrue(label.startsWith("Periodo Ene - Jun") || label.startsWith("Periodo Jul - Dic"))
    }

    private class FakeLoyaltyRepo(
        private val rewards: List<LoyaltyReward> = emptyList(),
        private val redeemedRewardIds: Set<String> = emptySet(),
        private val redemptionError: String? = null,
    ) : LoyaltyRepository {

        override suspend fun getCustomerByQr(qrPayload: String): Result<Customer?> =
            Result.success(null)

        override suspend fun getCustomerByPhone(phone: String): Result<Customer?> =
            Result.success(null)

        override suspend fun registerDailyVisit(qrPayload: String): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getRewards(includeInactive: Boolean): Result<List<LoyaltyReward>> =
            Result.success(if (includeInactive) rewards else rewards.filter { it.active })

        override suspend fun getCustomerRedeemedRewardIds(
            customerId: String,
            fromIso: String,
            toIso: String,
        ): Result<Set<String>> = Result.success(redeemedRewardIds)

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
        ): Result<RewardRedemption> {
            if (redemptionError != null) return Result.failure(IllegalStateException(redemptionError))
            val matched = rewards.firstOrNull { it.id == rewardId }
                ?: return Result.failure(IllegalArgumentException("Recompensa no encontrada"))
            return Result.success(
                RewardRedemption(
                    redemptionId = "red-1",
                    customerId = "cust-1",
                    rewardId = rewardId,
                    pointsCost = matched.pointsCost,
                    remainingPoints = 100 - matched.pointsCost,
                    alreadyProcessed = false,
                )
            )
        }
    }
}
