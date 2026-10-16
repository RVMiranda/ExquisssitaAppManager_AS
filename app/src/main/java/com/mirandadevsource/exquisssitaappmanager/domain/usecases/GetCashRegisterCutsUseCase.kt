package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import javax.inject.Inject

class GetCashRegisterCutsUseCase @Inject constructor(
    private val repository: CashRegisterRepository,
) {
    suspend operator fun invoke(businessDate: String): Result<List<DailyClosure>> =
        repository.getCutsForDay(businessDate)
}
