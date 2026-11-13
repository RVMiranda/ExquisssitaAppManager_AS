package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    fun getFlow(date: String): Flow<List<Expense>> = repository.getExpensesFlow(date)

    suspend fun refresh(date: String): Result<List<Expense>> = repository.refreshExpenses(date)
}
