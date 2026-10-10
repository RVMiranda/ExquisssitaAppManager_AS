package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import javax.inject.Inject

class GetEmployeesUseCase @Inject constructor(
    private val repository: EmployeeRepository,
) {
    suspend operator fun invoke(includeInactive: Boolean = false): Result<List<Employee>> =
        repository.getEmployees(includeInactive)
}
