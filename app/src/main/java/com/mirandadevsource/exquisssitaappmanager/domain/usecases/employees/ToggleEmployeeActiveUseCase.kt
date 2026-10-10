package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import javax.inject.Inject

class ToggleEmployeeActiveUseCase @Inject constructor(
    private val repository: EmployeeRepository,
) {
    suspend operator fun invoke(employeeId: String, active: Boolean): Result<Unit> {
        if (employeeId.isBlank()) {
            return Result.failure(IllegalArgumentException("El identificador del empleado es obligatorio."))
        }
        return repository.toggleEmployeeActive(employeeId.trim(), active)
    }
}
