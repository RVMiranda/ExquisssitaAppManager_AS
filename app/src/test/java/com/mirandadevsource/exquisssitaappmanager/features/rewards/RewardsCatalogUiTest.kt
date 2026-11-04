package com.mirandadevsource.exquisssitaappmanager.features.rewards

import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.GetLoyaltyRewardsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.ManageLoyaltyRewardUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardsCatalogUiTest {

    private val activeReward1 = LoyaltyReward(
        id = "rew-1",
        title = "Pastel de Chocolate",
        description = "Rebanada individual de pastel de tres leches",
        pointsCost = 50,
        active = true,
        syncVersion = 1L,
    )

    private val activeReward2 = LoyaltyReward(
        id = "rew-2",
        title = "Frappé Caramelo",
        description = "Bebida fría 16oz con jarabe de caramelo",
        pointsCost = 35,
        active = true,
        syncVersion = 2L,
    )

    private val inactiveReward = LoyaltyReward(
        id = "rew-3",
        title = "Galleta Gigante",
        description = "Galleta horneada con chispas",
        pointsCost = 20,
        active = false,
        syncVersion = 1L,
    )

    @Test
    fun `initial state calculations and filtering by status chips`() {
        val state = RewardsCatalogUiState(
            rewards = listOf(activeReward1, activeReward2, inactiveReward),
        )

        assertEquals(3, state.totalCount)
        assertEquals(2, state.activeCount)
        assertEquals(1, state.inactiveCount)

        // Filtro TODAS
        val todas = state.copy(selectedFilter = RewardFilter.TODAS)
        assertEquals(3, todas.filteredRewards.size)

        // Filtro ACTIVAS
        val activas = state.copy(selectedFilter = RewardFilter.ACTIVAS)
        assertEquals(2, activas.filteredRewards.size)
        assertTrue(activas.filteredRewards.all { it.active })

        // Filtro INACTIVAS
        val inactivas = state.copy(selectedFilter = RewardFilter.INACTIVAS)
        assertEquals(1, inactivas.filteredRewards.size)
        assertFalse(inactivas.filteredRewards.first().active)
    }

    @Test
    fun `filtering by search query matches title or description`() {
        val state = RewardsCatalogUiState(
            rewards = listOf(activeReward1, activeReward2, inactiveReward),
        )

        val searchPastel = state.copy(searchQuery = "pastel")
        assertEquals(1, searchPastel.filteredRewards.size)
        assertEquals("rew-1", searchPastel.filteredRewards.first().id)

        val searchBebida = state.copy(searchQuery = "bebida")
        assertEquals(1, searchBebida.filteredRewards.size)
        assertEquals("rew-2", searchBebida.filteredRewards.first().id)

        val searchNone = state.copy(searchQuery = "inexistente")
        assertTrue(searchNone.filteredRewards.isEmpty())
    }

    @Test
    fun `GetLoyaltyRewardsUseCase with includeInactive returns all rewards`() = runBlocking {
        val fakeRepo = FakeRewardsRepository(
            rewards = listOf(activeReward1, activeReward2, inactiveReward),
        )
        val useCase = GetLoyaltyRewardsUseCase(fakeRepo)

        // includeInactive = false (vista de meseros/cajeros)
        val onlyActive = useCase(includeInactive = false)
        assertTrue(onlyActive.isSuccess)
        assertEquals(2, onlyActive.getOrThrow().size)

        // includeInactive = true (vista de administrador)
        val allRewards = useCase(includeInactive = true)
        assertTrue(allRewards.isSuccess)
        assertEquals(3, allRewards.getOrThrow().size)
    }

    @Test
    fun `ManageLoyaltyRewardUseCase creates reward with expectedVersion 0`() = runBlocking {
        val fakeRepo = FakeRewardsRepository()
        val useCase = ManageLoyaltyRewardUseCase(fakeRepo)

        val result = useCase(
            rewardId = "new-id",
            title = "Nueva Recompensa",
            description = "Descripción de prueba",
            pointsCost = 60,
            active = true,
            expectedVersion = 0L,
        )

        assertTrue(result.isSuccess)
        val saved = result.getOrThrow()
        assertEquals("Nueva Recompensa", saved.title)
        assertEquals(60, saved.pointsCost)
        assertEquals(0L, fakeRepo.lastExpectedVersion)
        assertEquals(1L, saved.syncVersion)
    }

    @Test
    fun `ManageLoyaltyRewardUseCase propagates optimistic locking version conflict error`() = runBlocking {
        val fakeRepo = FakeRewardsRepository(
            rewards = listOf(activeReward1),
            manageError = "version_conflict P0004",
        )
        val useCase = ManageLoyaltyRewardUseCase(fakeRepo)

        val result = useCase(
            rewardId = "rew-1",
            title = "Pastel Modificado",
            description = "Descripción",
            pointsCost = 50,
            active = true,
            expectedVersion = 1L,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("version_conflict") == true)
        assertEquals(1L, fakeRepo.lastExpectedVersion)
    }

    private class FakeRewardsRepository(
        private var rewards: List<LoyaltyReward> = emptyList(),
        private val manageError: String? = null,
    ) : LoyaltyRepository {

        var lastSavedReward: LoyaltyReward? = null
        var lastExpectedVersion: Long? = null

        override suspend fun getCustomerByQr(qrPayload: String): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer?> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getCustomerByPhone(phone: String): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer?> =
            Result.failure(UnsupportedOperationException())

        override suspend fun registerDailyVisit(qrPayload: String): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getCustomerRedeemedRewardIds(customerId: String, fromIso: String, toIso: String): Result<Set<String>> =
            Result.success(emptySet())

        override suspend fun redeemReward(qrPayload: String, rewardId: String, idempotencyKey: String): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getRewards(includeInactive: Boolean): Result<List<LoyaltyReward>> =
            Result.success(if (includeInactive) rewards else rewards.filter { it.active })

        override suspend fun manageReward(
            rewardId: String,
            title: String,
            description: String,
            pointsCost: Int,
            active: Boolean,
            expectedVersion: Long,
        ): Result<LoyaltyReward> {
            lastExpectedVersion = expectedVersion
            if (manageError != null) return Result.failure(IllegalStateException(manageError))
            val saved = LoyaltyReward(
                id = rewardId,
                title = title,
                description = description,
                pointsCost = pointsCost,
                active = active,
                syncVersion = expectedVersion + 1,
            )
            lastSavedReward = saved
            return Result.success(saved)
        }
    }
}
