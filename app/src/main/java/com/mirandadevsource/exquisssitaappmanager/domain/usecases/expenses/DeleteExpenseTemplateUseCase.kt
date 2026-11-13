package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import javax.inject.Inject

class DeleteExpenseTemplateUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(templateId: String): Result<Unit> {
        if (templateId.isBlank()) {
            return Result.failure(IllegalArgumentException("El identificador de la plantilla no puede estar vacío."))
        }
        return repository.deleteTemplate(templateId)
    }

    suspend fun toggleActive(templateId: String, active: Boolean): Result<Unit> {
        if (templateId.isBlank()) {
            return Result.failure(IllegalArgumentException("El identificador de la plantilla no puede estar vacío."))
        }
        return repository.toggleTemplateActive(templateId, active)
    }
}
