package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import javax.inject.Inject
import kotlin.math.roundToLong

class SaveExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(expense: Expense): Result<Expense> {
        if (expense.concept.isBlank()) {
            return Result.failure(IllegalArgumentException("El concepto del gasto no puede estar vacío."))
        }
        if (expense.date.isBlank()) {
            return Result.failure(IllegalArgumentException("La fecha del gasto no puede estar vacía."))
        }

        // Si se proveen cantidad y precio unitario válidos pero el monto no se calculó, derivarlo
        val calculatedAmount = if (expense.amountCents <= 0L && expense.quantity != null && expense.unitPriceCents != null) {
            (expense.quantity * expense.unitPriceCents).roundToLong()
        } else {
            expense.amountCents
        }

        if (calculatedAmount <= 0L) {
            return Result.failure(IllegalArgumentException("El monto del gasto debe ser mayor a cero."))
        }

        val normalizedExpense = expense.copy(
            concept = expense.concept.trim(),
            category = expense.category.trim(),
            amountCents = calculatedAmount,
            notes = expense.notes?.trim()?.ifBlank { null },
        )

        return repository.saveExpense(normalizedExpense)
    }
}
