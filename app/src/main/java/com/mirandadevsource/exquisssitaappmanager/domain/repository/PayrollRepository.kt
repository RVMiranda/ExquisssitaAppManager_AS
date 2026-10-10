package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment

class DuplicatePaymentException(cause: Throwable? = null) :
    IllegalStateException("Ya existe un pago registrado para este empleado en la semana seleccionada.", cause)

interface PayrollRepository {
    suspend fun getPaymentsForPeriod(periodoInicio: String): Result<List<EmployeePayment>>
    suspend fun getPaymentHistoryForEmployee(empleadoId: String): Result<List<EmployeePayment>>
    suspend fun registerWeeklyPayment(
        empleadoId: String,
        periodoInicio: String,
        fechaPago: String? = null,
        notas: String? = null,
    ): Result<EmployeePayment>
}
