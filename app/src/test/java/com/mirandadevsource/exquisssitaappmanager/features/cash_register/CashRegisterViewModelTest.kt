package com.mirandadevsource.exquisssitaappmanager.features.cash_register

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.CloseCashRegisterUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.GetCashRegisterCutsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.GetCashRegisterDaySummaryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CashRegisterViewModelTest {

    private val today = LocalDate.now(CashRegisterUiState.BUSINESS_ZONE).toString()
    private val pastDate = "2026-10-10"

    private val sampleCuts = listOf(
        DailyClosure(
            id = "cut-1",
            businessDate = today,
            cutNumber = 1,
            periodStart = "${today}T00:00:00Z",
            periodEnd = "${today}T15:00:00Z",
            cashCents = 150000L, // $1,500.00
            cardCents = 250000L, // $2,500.00
            otherCents = 0L,
            totalCents = 400000L, // $4,000.00
            closedOrderCount = 12,
            closedAt = "${today}T15:05:00Z",
            closedBy = "emp-uuid-1",
            syncVersion = 1,
        ),
        DailyClosure(
            id = "cut-2",
            businessDate = today,
            cutNumber = 2,
            periodStart = "${today}T15:00:00Z",
            periodEnd = "${today}T21:00:00Z",
            cashCents = 200000L, // $2,000.00
            cardCents = 300000L, // $3,000.00
            otherCents = 50000L,  // $500.00
            totalCents = 550000L, // $5,500.00
            closedOrderCount = 18,
            closedAt = "${today}T21:05:00Z",
            closedBy = "emp-uuid-1",
            syncVersion = 1,
        ),
    )

    private val sampleSummary = CashRegisterDaySummary(
        businessDate = today,
        cutCount = 2,
        cashCents = 350000L,
        cardCents = 550000L,
        otherCents = 50000L,
        totalCents = 950000L,
        closedOrderCount = 30,
    )

    private class FakeCashRegisterRepository(
        initialCuts: List<DailyClosure> = emptyList(),
        var isOnline: Boolean = true,
        var hasOpenOrders: Boolean = false,
    ) : CashRegisterRepository {
        val cuts = initialCuts.toMutableList()

        override suspend fun closeCurrentPeriod(): Result<DailyClosure> {
            if (!isOnline) {
                return Result.failure(IllegalStateException("El corte requiere conexión y una sesión remota válida."))
            }
            if (hasOpenOrders) {
                return Result.failure(IllegalStateException("No se puede realizar el corte mientras haya comandas abiertas."))
            }
            if (cuts.size >= 5) {
                return Result.failure(IllegalStateException("Ya se alcanzó el máximo de cinco cortes del día."))
            }
            val nextNumber = cuts.size + 1
            val todayStr = LocalDate.now(CashRegisterUiState.BUSINESS_ZONE).toString()
            val newCut = DailyClosure(
                id = "cut-$nextNumber",
                businessDate = todayStr,
                cutNumber = nextNumber,
                periodStart = "${todayStr}T10:00:00Z",
                periodEnd = "${todayStr}T16:00:00Z",
                cashCents = 100000L,
                cardCents = 150000L,
                otherCents = 0L,
                totalCents = 250000L,
                closedOrderCount = 8,
                closedAt = "${todayStr}T16:02:00Z",
                closedBy = "emp-admin",
                syncVersion = 1,
            )
            cuts.add(newCut)
            return Result.success(newCut)
        }

        override suspend fun getCutsForDay(businessDate: String): Result<List<DailyClosure>> {
            return Result.success(cuts.filter { it.businessDate == businessDate })
        }

        override suspend fun getDaySummary(businessDate: String): Result<CashRegisterDaySummary> {
            val dayCuts = cuts.filter { it.businessDate == businessDate }
            return Result.success(
                CashRegisterDaySummary(
                    businessDate = businessDate,
                    cutCount = dayCuts.size,
                    cashCents = dayCuts.sumOf { it.cashCents },
                    cardCents = dayCuts.sumOf { it.cardCents },
                    otherCents = dayCuts.sumOf { it.otherCents },
                    totalCents = dayCuts.sumOf { it.totalCents },
                    closedOrderCount = dayCuts.sumOf { it.closedOrderCount },
                )
            )
        }
    }

    @Test
    fun `CashRegisterUiState isToday detects current business date correctly`() {
        val todayState = CashRegisterUiState(selectedDate = today)
        assertTrue(todayState.isToday)

        val pastState = CashRegisterUiState(selectedDate = pastDate)
        assertFalse(pastState.isToday)
    }

    @Test
    fun `CashRegisterUiState nextCutNumber calculates next consecutive up to 5`() {
        val emptyState = CashRegisterUiState(cuts = emptyList())
        assertEquals(1, emptyState.nextCutNumber)

        val twoCutsState = CashRegisterUiState(cuts = sampleCuts)
        assertEquals(3, twoCutsState.nextCutNumber)

        val fiveCuts = List(5) { index ->
            sampleCuts[0].copy(id = "c-$index", cutNumber = index + 1)
        }
        val fullState = CashRegisterUiState(cuts = fiveCuts)
        assertEquals(5, fullState.nextCutNumber)
        assertTrue(fullState.maxCutsReached)
    }

    @Test
    fun `CashRegisterUiState canPerformCut allows cut only under compliant conditions`() {
        val validState = CashRegisterUiState(
            selectedDate = today,
            isOnline = true,
            openOrdersCount = 0,
            cuts = sampleCuts, // 2 cuts
            userRole = UserRole.ADMIN,
        )
        assertTrue(validState.canPerformCut)

        // Gerente también puede
        val gerenteState = validState.copy(userRole = UserRole.GERENTE)
        assertTrue(gerenteState.canPerformCut)

        // Mesero bloqueado
        val meseroState = validState.copy(userRole = UserRole.MESERO)
        assertFalse(meseroState.canPerformCut)

        // Bloqueado si hay comandas abiertas
        val openOrdersState = validState.copy(openOrdersCount = 2)
        assertFalse(openOrdersState.canPerformCut)

        // Bloqueado si está offline
        val offlineState = validState.copy(isOnline = false)
        assertFalse(offlineState.canPerformCut)

        // Bloqueado si no es la fecha de hoy
        val pastState = validState.copy(selectedDate = pastDate)
        assertFalse(pastState.canPerformCut)

        // Bloqueado si se alcanzaron 5 cortes
        val fiveCuts = List(5) { index ->
            sampleCuts[0].copy(id = "c-$index", cutNumber = index + 1)
        }
        val maxCutsState = validState.copy(cuts = fiveCuts)
        assertFalse(maxCutsState.canPerformCut)
    }

    @Test
    fun `CloseCashRegisterUseCase executes cut successfully and updates cuts count`() = runBlocking {
        val repo = FakeCashRegisterRepository(sampleCuts)
        val closeUseCase = CloseCashRegisterUseCase(repo)
        val getCutsUseCase = GetCashRegisterCutsUseCase(repo)
        val getSummaryUseCase = GetCashRegisterDaySummaryUseCase(repo)

        val result = closeUseCase()
        assertTrue(result.isSuccess)
        val closure = result.getOrThrow()
        assertEquals(3, closure.cutNumber)
        assertEquals(today, closure.businessDate)

        val updatedCuts = getCutsUseCase(today).getOrThrow()
        assertEquals(3, updatedCuts.size)

        val updatedSummary = getSummaryUseCase(today).getOrThrow()
        assertEquals(3, updatedSummary.cutCount)
        assertEquals(1200000L, updatedSummary.totalCents) // 950000 + 250000
    }

    @Test
    fun `CloseCashRegisterUseCase rejects cut when open orders exist`() = runBlocking {
        val repo = FakeCashRegisterRepository(sampleCuts, hasOpenOrders = true)
        val closeUseCase = CloseCashRegisterUseCase(repo)

        val result = closeUseCase()
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("comandas abiertas"))
    }

    @Test
    fun `CloseCashRegisterUseCase rejects cut when offline`() = runBlocking {
        val repo = FakeCashRegisterRepository(sampleCuts, isOnline = false)
        val closeUseCase = CloseCashRegisterUseCase(repo)

        val result = closeUseCase()
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("requiere conexión"))
    }

    @Test
    fun `CloseCashRegisterUseCase rejects cut when maximum of five cuts is reached`() = runBlocking {
        val fiveCuts = List(5) { index ->
            sampleCuts[0].copy(id = "c-$index", cutNumber = index + 1)
        }
        val repo = FakeCashRegisterRepository(fiveCuts)
        val closeUseCase = CloseCashRegisterUseCase(repo)

        val result = closeUseCase()
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("cinco cortes"))
    }

    @Test
    fun `GetCashRegisterCutsUseCase retrieves cuts for historical date`() = runBlocking {
        val pastCut = sampleCuts[0].copy(id = "past-1", businessDate = pastDate)
        val repo = FakeCashRegisterRepository(listOf(pastCut) + sampleCuts)
        val getCutsUseCase = GetCashRegisterCutsUseCase(repo)

        val pastResult = getCutsUseCase(pastDate).getOrThrow()
        assertEquals(1, pastResult.size)
        assertEquals(pastDate, pastResult.first().businessDate)

        val todayResult = getCutsUseCase(today).getOrThrow()
        assertEquals(2, todayResult.size)
    }
}
