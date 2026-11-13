package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveExpenseTemplateUseCaseTest {

    private lateinit var repository: FakeExpenseRepository
    private lateinit var useCase: SaveExpenseTemplateUseCase

    @Before
    fun setUp() {
        repository = FakeExpenseRepository()
        useCase = SaveExpenseTemplateUseCase(repository)
    }

    @Test
    fun invoke_rejectsBlankName() = runBlocking {
        val result = useCase(
            name = "   ",
            category = "Insumos",
            unitPriceCents = 2400L,
            unitOfMeasure = "kg",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repository.saveTemplateCalls)
    }

    @Test
    fun invoke_rejectsNegativeUnitPrice() = runBlocking {
        val result = useCase(
            name = "Tortilla",
            category = "Insumos",
            unitPriceCents = -100L,
            unitOfMeasure = "kg",
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repository.saveTemplateCalls)
    }

    @Test
    fun invoke_savesValidTemplateWithDefaultFallbacks() = runBlocking {
        val result = useCase(
            name = "  Tortilla de comal  ",
            category = "  ",
            unitPriceCents = 2400L,
            unitOfMeasure = "  ",
            isActive = true,
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.saveTemplateCalls)
        val saved = repository.lastSavedTemplate
        assertEquals("Tortilla de comal", saved?.name)
        assertEquals("Insumos", saved?.category)
        assertEquals("pza", saved?.unitOfMeasure)
        assertEquals(2400L, saved?.unitPriceCents)
        assertEquals(true, saved?.isActive)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        var saveTemplateCalls = 0
        var lastSavedTemplate: ExpenseTemplate? = null

        override fun getExpensesFlow(date: String): Flow<List<Expense>> = flowOf(emptyList())
        override suspend fun refreshExpenses(date: String): Result<List<Expense>> = Result.success(emptyList())
        override suspend fun saveExpense(expense: Expense): Result<Expense> = Result.success(expense)
        override suspend fun deleteExpense(localId: String): Result<Unit> = Result.success(Unit)
        override suspend fun syncPendingExpenses(): Result<Int> = Result.success(0)
        override fun getTemplatesFlow(): Flow<List<ExpenseTemplate>> = flowOf(emptyList())
        override fun getAllTemplatesFlow(): Flow<List<ExpenseTemplate>> = flowOf(emptyList())
        override suspend fun refreshTemplates(): Result<List<ExpenseTemplate>> = Result.success(emptyList())

        override suspend fun saveTemplate(template: ExpenseTemplate): Result<ExpenseTemplate> {
            saveTemplateCalls++
            lastSavedTemplate = template
            return Result.success(template)
        }

        override suspend fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleTemplateActive(templateId: String, active: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun getDailyProfitSummary(date: String): Result<DailyProfitSummary> =
            Result.failure(UnsupportedOperationException())
    }
}
