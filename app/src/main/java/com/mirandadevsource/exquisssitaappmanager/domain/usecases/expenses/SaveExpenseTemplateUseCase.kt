package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import javax.inject.Inject

class SaveExpenseTemplateUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(
        id: String? = null,
        name: String,
        category: String,
        unitPriceCents: Long,
        unitOfMeasure: String,
        isActive: Boolean = true,
    ): Result<ExpenseTemplate> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre del insumo o producto frecuente es obligatorio."))
        }

        if (unitPriceCents < 0L) {
            return Result.failure(IllegalArgumentException("El precio unitario no puede ser negativo."))
        }

        val trimmedCategory = category.trim().ifBlank { "Insumos" }
        val trimmedUnit = unitOfMeasure.trim().ifBlank { "pza" }

        val template = ExpenseTemplate(
            localId = id.orEmpty(),
            remoteId = id?.ifBlank { null },
            name = trimmedName,
            category = trimmedCategory,
            unitPriceCents = unitPriceCents,
            unitOfMeasure = trimmedUnit,
            isActive = isActive,
        )

        return repository.saveTemplate(template)
    }
}
