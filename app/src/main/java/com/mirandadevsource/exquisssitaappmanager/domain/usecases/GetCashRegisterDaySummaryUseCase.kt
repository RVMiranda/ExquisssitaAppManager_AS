package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import javax.inject.Inject

class GetCashRegisterDaySummaryUseCase @Inject constructor(
    private val repository: CashRegisterRepository,
) {
    suspend operator fun invoke(businessDate: String): Result<CashRegisterDaySummary> =
        repository.getDaySummary(businessDate)
}
