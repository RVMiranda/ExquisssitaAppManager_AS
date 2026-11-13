package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveExpenseUseCaseTest {

    @Test
    fun rejectsBlankConcept() = runBlocking {
        val repo = FakeExpenseRepository()
        val useCase = SaveExpenseUseCase(repo)
        val expense = Expense(
            localId = "local-1",
            date = "2026-11-12",
            concept = "   ",
            amountCents = 10000L,
        )

        val result = useCase(expense)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun rejectsNonPositiveAmountWhenNoQuantityOrUnitPrice() = runBlocking {
        val repo = FakeExpenseRepository()
        val useCase = SaveExpenseUseCase(repo)
        val expense = Expense(
            localId = "local-1",
            date = "2026-11-12",
            concept = "Gas LP",
            amountCents = 0L,
        )

        val result = useCase(expense)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repo.saveCalls)
    }

    @Test
    fun calculatesAmountFromQuantityAndUnitPriceWhenAmountZero() = runBlocking {
        val repo = FakeExpenseRepository()
        val useCase = SaveExpenseUseCase(repo)
        val expense = Expense(
            localId = "local-1",
            date = "2026-11-12",
            concept = "Tortilla de comal",
            amountCents = 0L,
            quantity = 10.0,
            unitPriceCents = 2400L, // $24.00 MXN / kg
        )

        val result = useCase(expense)
        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveCalls)
        assertEquals(24000L, repo.lastSavedExpense?.amountCents) // $240.00 MXN
    }

    @Test
    fun acceptsValidManualExpenseAndTrimsFields() = runBlocking {
        val repo = FakeExpenseRepository()
        val useCase = SaveExpenseUseCase(repo)
        val expense = Expense(
            localId = "local-1",
            date = "2026-11-12",
            concept = "  Reja de Refresco Coca-Cola  ",
            category = " Insumos ",
            amountCents = 29000L,
            paymentMethod = ExpensePaymentMethod.EFECTIVO,
            notes = "  Pagado con efectivo de caja  ",
        )

        val result = useCase(expense)
        assertTrue(result.isSuccess)
        assertEquals("Reja de Refresco Coca-Cola", repo.lastSavedExpense?.concept)
        assertEquals("Insumos", repo.lastSavedExpense?.category)
        assertEquals(29000L, repo.lastSavedExpense?.amountCents)
        assertEquals("Pagado con efectivo de caja", repo.lastSavedExpense?.notes)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        var saveCalls = 0
        var lastSavedExpense: Expense? = null

        override fun getExpensesFlow(date: String): Flow<List<Expense>> = flowOf(emptyList())
        override suspend fun refreshExpenses(date: String): Result<List<Expense>> = Result.success(emptyList())

        override suspend fun saveExpense(expense: Expense): Result<Expense> {
            saveCalls++
            lastSavedExpense = expense
            return Result.success(expense)
        }

        override suspend fun deleteExpense(localId: String): Result<Unit> = Result.success(Unit)
        override suspend fun syncPendingExpenses(): Result<Int> = Result.success(0)
        override fun getTemplatesFlow(): Flow<List<ExpenseTemplate>> = flowOf(emptyList())
        override suspend fun refreshTemplates(): Result<List<ExpenseTemplate>> = Result.success(emptyList())
        override suspend fun getDailyProfitSummary(date: String): Result<DailyProfitSummary> =
            Result.failure(UnsupportedOperationException())
    }
}
