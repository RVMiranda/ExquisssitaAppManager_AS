package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import javax.inject.Inject

class RegisterLoanPaymentUseCase @Inject constructor(
    private val repository: EmployeeLoanRepository,
) {
    suspend operator fun invoke(
        prestamoId: String,
        montoCentavos: Long,
        tipo: String = "pago_directo",
        notas: String? = null,
    ): Result<EmployeeLoan> {
        if (prestamoId.isBlank()) {
            return Result.failure(IllegalArgumentException("Identificador de prestamo no valido."))
        }
        if (montoCentavos <= 0L) {
            return Result.failure(IllegalArgumentException("El monto del abono debe ser mayor a cero."))
        }
        return repository.registerPayment(
            prestamoId = prestamoId,
            montoCentavos = montoCentavos,
            tipo = tipo,
            notas = notas?.trim()?.ifBlank { null },
        )
    }
}
