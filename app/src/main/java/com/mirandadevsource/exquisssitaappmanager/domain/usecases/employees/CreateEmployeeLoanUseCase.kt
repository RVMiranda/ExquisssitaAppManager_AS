package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import javax.inject.Inject

class CreateEmployeeLoanUseCase @Inject constructor(
    private val repository: EmployeeLoanRepository,
) {
    suspend operator fun invoke(
        empleadoId: String,
        montoCentavos: Long,
        fecha: String? = null,
        notas: String? = null,
    ): Result<EmployeeLoan> {
        if (empleadoId.isBlank()) {
            return Result.failure(IllegalArgumentException("Debes seleccionar un empleado valido."))
        }
        if (montoCentavos <= 0L) {
            return Result.failure(IllegalArgumentException("El monto del prestamo o adelanto debe ser mayor a cero."))
        }
        return repository.createLoan(
            empleadoId = empleadoId,
            montoCentavos = montoCentavos,
            fecha = fecha,
            notas = notas?.trim()?.ifBlank { null },
        )
    }
}
