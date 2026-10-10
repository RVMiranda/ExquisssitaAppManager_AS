package com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

class RegisterPayrollPaymentUseCase @Inject constructor(
    private val repository: PayrollRepository,
) {
    suspend operator fun invoke(
        empleadoId: String,
        periodoInicio: String,
        fechaPago: String? = null,
        notas: String? = null,
    ): Result<EmployeePayment> {
        if (empleadoId.isBlank()) {
            return Result.failure(IllegalArgumentException("El empleado es obligatorio para registrar el pago."))
        }
        val periodDate = runCatching { LocalDate.parse(periodoInicio) }
            .getOrElse { return Result.failure(IllegalArgumentException("El periodo debe ser una fecha válida (AAAA-MM-DD).", it)) }
        if (periodDate.dayOfWeek != DayOfWeek.MONDAY) {
            return Result.failure(IllegalArgumentException("El periodo semanal debe iniciar en lunes."))
        }
        if (fechaPago != null && runCatching { LocalDate.parse(fechaPago) }.isFailure) {
            return Result.failure(IllegalArgumentException("La fecha de pago debe usar el formato AAAA-MM-DD."))
        }
        return repository.registerWeeklyPayment(
            empleadoId = empleadoId.trim(),
            periodoInicio = periodDate.toString(),
            fechaPago = fechaPago,
            notas = notas?.trim()?.ifBlank { null },
        )
    }
}
