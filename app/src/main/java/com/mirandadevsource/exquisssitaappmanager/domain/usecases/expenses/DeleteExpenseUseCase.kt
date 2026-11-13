package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import javax.inject.Inject

class DeleteExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(localId: String): Result<Unit> {
        if (localId.isBlank()) {
            return Result.failure(IllegalArgumentException("El identificador del gasto es inválido."))
        }
        return repository.deleteExpense(localId)
    }
}
