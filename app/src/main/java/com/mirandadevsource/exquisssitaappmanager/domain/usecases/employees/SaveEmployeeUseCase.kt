package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import javax.inject.Inject

class SaveEmployeeUseCase @Inject constructor(
    private val repository: EmployeeRepository,
) {
    suspend operator fun invoke(
        nombre: String,
        apellido: String,
        telefono: String?,
        salarioCents: Long,
        diaPago: Int,
        employeeId: String? = null,
        activo: Boolean = true,
    ): Result<Employee> {
        val cleanName = nombre.trim()
        val cleanSurname = apellido.trim()
        if (cleanName.isEmpty() || cleanSurname.isEmpty()) {
            return Result.failure(IllegalArgumentException("El nombre y apellido son obligatorios."))
        }
        if (salarioCents <= 0) {
            return Result.failure(IllegalArgumentException("La cuota semanal debe ser mayor que cero."))
        }
        if (diaPago !in 1..7) {
            return Result.failure(IllegalArgumentException("El día de pago debe estar entre lunes (1) y domingo (7)."))
        }
        val cleanPhone = telefono?.trim()?.ifBlank { null }
        val normalizedId = employeeId?.trim()?.ifBlank { null }
        return if (normalizedId == null) {
            repository.createEmployee(cleanName, cleanSurname, cleanPhone, salarioCents, diaPago)
        } else {
            repository.updateEmployee(
                normalizedId, cleanName, cleanSurname, cleanPhone, salarioCents, diaPago, activo,
            )
        }
    }
}
