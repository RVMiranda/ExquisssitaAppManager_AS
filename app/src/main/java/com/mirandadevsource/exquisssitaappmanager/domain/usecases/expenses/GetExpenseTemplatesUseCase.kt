package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExpenseTemplatesUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    fun getFlow(): Flow<List<ExpenseTemplate>> = repository.getTemplatesFlow()

    suspend fun refresh(): Result<List<ExpenseTemplate>> = repository.refreshTemplates()
}
