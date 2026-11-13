package com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import javax.inject.Inject

class GetDailyProfitSummaryUseCase @Inject constructor(
    private val repository: ExpenseRepository,
) {
    suspend operator fun invoke(date: String): Result<DailyProfitSummary> {
        if (date.isBlank()) {
            return Result.failure(IllegalArgumentException("La fecha no puede estar vacía."))
        }
        return repository.getDailyProfitSummary(date)
    }
}
