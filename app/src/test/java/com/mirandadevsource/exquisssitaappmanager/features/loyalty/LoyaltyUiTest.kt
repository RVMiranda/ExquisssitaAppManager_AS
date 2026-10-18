package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.FindCustomerUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty.RegisterCustomerVisitUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LoyaltyUiTest {

    private val sampleCustomer = Customer(
        id = "cust-123",
        name = "María Gómez",
        phone = "9611234567",
        totalVisits = 5,
        loyaltyPoints = 50,
        registeredAt = "2026-10-01T12:00:00Z",
        fetchedAt = Instant.now(),
        isCached = false,
    )

    private val cachedCustomer = sampleCustomer.copy(
        isCached = true,
        fetchedAt = Instant.now().minusSeconds(3600),
    )

    @Test
    fun `initial LoyaltyUiState starts with tab 0 and empty customer`() {
        val state = LoyaltyUiState()
        assertEquals(0, state.selectedTab)
        assertEquals("", state.phoneInput)
        assertEquals("", state.qrTokenInput)
        assertFalse(state.isSearching)
        assertFalse(state.isRegisteringVisit)
        assertNull(state.customer)
        assertFalse(state.hasScannedQrToken)
        assertFalse(state.customerNotFound)
        assertTrue(state.isOnline)
        assertNull(state.successNotification)
        assertNull(state.warningNotification)
        assertNull(state.errorNotification)
        assertNull(state.rewardNotice)
    }

    @Test
    fun `phone validation enables search only for 7 to 10 digit numbers`() {
        val emptyState = LoyaltyUiState(phoneInput = "")
        assertFalse(emptyState.canSearchPhone)

        val shortState = LoyaltyUiState(phoneInput = "123456")
        assertFalse(shortState.canSearchPhone)

        val validSeven = LoyaltyUiState(phoneInput = "1234567")
        assertTrue(validSeven.canSearchPhone)

        val validTen = LoyaltyUiState(phoneInput = "9611234567")
        assertTrue(validTen.canSearchPhone)

        val searchingState = LoyaltyUiState(phoneInput = "9611234567", isSearching = true)
        assertFalse(searchingState.canSearchPhone)
    }

    @Test
    fun `canRegisterVisit is false when offline`() {
        val state = LoyaltyUiState(
            isOnline = false,
            customer = sampleCustomer,
            hasScannedQrToken = true,
        )
        assertFalse(state.canRegisterVisit)
    }

    @Test
    fun `canRegisterVisit is false when customer was searched by phone only`() {
        val state = LoyaltyUiState(
            isOnline = true,
            customer = sampleCustomer,
            hasScannedQrToken = false, // Buscado por teléfono, sin token QR
        )
        assertFalse(state.canRegisterVisit)
    }

    @Test
    fun `canRegisterVisit is false when customer profile is cached`() {
        val state = LoyaltyUiState(
            isOnline = true,
            customer = cachedCustomer, // Ficha en caché offline
            hasScannedQrToken = true,
        )
        assertFalse(state.canRegisterVisit)
    }

    @Test
    fun `canRegisterVisit is true when online, has scanned QR, and profile is live`() {
        val state = LoyaltyUiState(
            isOnline = true,
            customer = sampleCustomer,
            hasScannedQrToken = true,
            isRegisteringVisit = false,
        )
        assertTrue(state.canRegisterVisit)
    }

    @Test
    fun `canRegisterVisit is false while visit is already in progress`() {
        val state = LoyaltyUiState(
            isOnline = true,
            customer = sampleCustomer,
            hasScannedQrToken = true,
            isRegisteringVisit = true,
        )
        assertFalse(state.canRegisterVisit)
    }

    @Test
    fun `FindCustomerUseCase byPhone returns live customer`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(sampleCustomer))
        val useCase = FindCustomerUseCase(repo)

        val result = useCase.byPhone("9611234567")
        assertTrue(result.isSuccess)
        val customer = result.getOrThrow()
        assertNotNull(customer)
        assertEquals("cust-123", customer?.id)
        assertEquals("María Gómez", customer?.name)
        assertFalse(customer?.isCached ?: true)
    }

    @Test
    fun `FindCustomerUseCase byPhone returns cached customer with fetchedAt`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(cachedCustomer))
        val useCase = FindCustomerUseCase(repo)

        val result = useCase.byPhone("9611234567")
        assertTrue(result.isSuccess)
        val customer = result.getOrThrow()
        assertNotNull(customer)
        assertTrue(customer?.isCached ?: false)
        assertNotNull(customer?.fetchedAt)
    }

    @Test
    fun `FindCustomerUseCase byPhone returns null for non-existent customer`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = emptyList())
        val useCase = FindCustomerUseCase(repo)

        val result = useCase.byPhone("9999999999")
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `FindCustomerUseCase byQr returns customer on valid opaque token`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(sampleCustomer))
        val useCase = FindCustomerUseCase(repo)

        val validToken = "x".repeat(64)
        val result = useCase.byQr(validToken)
        assertTrue(result.isSuccess)
        val customer = result.getOrThrow()
        assertNotNull(customer)
        assertEquals("cust-123", customer?.id)
    }

    @Test
    fun `RegisterCustomerVisitUseCase rejects tokens shorter than 32 chars without repository call`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(sampleCustomer))
        val useCase = RegisterCustomerVisitUseCase(repo)

        val result = useCase("too_short_token")
        assertTrue(result.isFailure)
        assertNull(repo.lastRegisteredQrToken)
    }

    @Test
    fun `RegisterCustomerVisitUseCase rejects tokens longer than 512 chars without repository call`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(sampleCustomer))
        val useCase = RegisterCustomerVisitUseCase(repo)

        val result = useCase("t".repeat(513))
        assertTrue(result.isFailure)
        assertNull(repo.lastRegisteredQrToken)
    }

    @Test
    fun `RegisterCustomerVisitUseCase processes valid token and updates points and visits`() = runBlocking {
        val repo = FakeLoyaltyRepository(customers = listOf(sampleCustomer))
        val useCase = RegisterCustomerVisitUseCase(repo)

        val validToken = "valid_opaque_token_".padEnd(48, 'a')
        val result = useCase(validToken)
        assertTrue(result.isSuccess)
        val visit = result.getOrThrow()
        assertEquals("cust-123", visit.customerId)
        assertEquals(10, visit.pointsAwarded)
        assertEquals(6, visit.totalVisits)
        assertEquals(60, visit.totalLoyaltyPoints)
        assertFalse(visit.alreadyRegisteredToday)
        assertEquals(validToken, repo.lastRegisteredQrToken)
    }

    @Test
    fun `RegisterCustomerVisitUseCase detects alreadyRegisteredToday and returns correct flag`() = runBlocking {
        val repo = FakeLoyaltyRepository(
            customers = listOf(sampleCustomer),
            alreadyRegisteredToday = true,
        )
        val useCase = RegisterCustomerVisitUseCase(repo)

        val validToken = "valid_opaque_token_".padEnd(48, 'b')
        val result = useCase(validToken)
        assertTrue(result.isSuccess)
        val visit = result.getOrThrow()
        assertTrue(visit.alreadyRegisteredToday)
        assertEquals(0, visit.pointsAwarded)
        assertEquals(5, visit.totalVisits)
        assertEquals(50, visit.totalLoyaltyPoints)
    }

    private class FakeLoyaltyRepository(
        private val customers: List<Customer> = emptyList(),
        private val alreadyRegisteredToday: Boolean = false,
    ) : LoyaltyRepository {
        var lastRegisteredQrToken: String? = null

        override suspend fun getCustomerByQr(qrPayload: String): Result<Customer?> {
            return Result.success(customers.firstOrNull())
        }

        override suspend fun getCustomerByPhone(phone: String): Result<Customer?> {
            return Result.success(customers.firstOrNull { it.phone == phone })
        }

        override suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit> {
            lastRegisteredQrToken = qrPayload
            val customer = customers.firstOrNull() ?: return Result.failure(NoSuchElementException("Cliente no encontrado"))
            return if (alreadyRegisteredToday) {
                Result.success(
                    CustomerVisit(
                        customerId = customer.id,
                        date = "2026-10-17",
                        pointsAwarded = 0,
                        totalVisits = customer.totalVisits,
                        totalLoyaltyPoints = customer.loyaltyPoints,
                        alreadyRegisteredToday = true,
                    )
                )
            } else {
                Result.success(
                    CustomerVisit(
                        customerId = customer.id,
                        date = "2026-10-17",
                        pointsAwarded = 10,
                        totalVisits = customer.totalVisits + 1,
                        totalLoyaltyPoints = customer.loyaltyPoints + 10,
                        alreadyRegisteredToday = false,
                    )
                )
            }
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
