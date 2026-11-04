package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import javax.inject.Inject

class GetEmployeeLoansUseCase @Inject constructor(
    private val repository: EmployeeLoanRepository,
) {
    suspend operator fun invoke(): Result<List<EmployeeLoan>> {
        return repository.getAllLoans()
    }
}
