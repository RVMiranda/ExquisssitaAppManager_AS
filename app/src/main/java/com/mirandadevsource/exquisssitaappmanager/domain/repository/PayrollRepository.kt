package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList

class DuplicatePaymentException(cause: Throwable? = null) :
    IllegalStateException("Ya existe un pago registrado para este empleado en la semana seleccionada.", cause)

interface PayrollRepository {
    suspend fun getPaymentsForPeriod(periodoInicio: String): Result<List<EmployeePayment>>
    suspend fun getPaymentHistoryForEmployee(empleadoId: String): Result<List<EmployeePayment>>
    suspend fun getPaymentHistoryPage(empleadoId: String, page: PageRequest = PageRequest()): Result<DataPage<EmployeePayment>> =
        getPaymentHistoryForEmployee(empleadoId).map { pageFromCompleteList(it, page, "payroll-history") }
    suspend fun registerWeeklyPayment(
        empleadoId: String,
        periodoInicio: String,
        fechaPago: String? = null,
        notas: String? = null,
        descuentoPrestamoCents: Long = 0L,
        prestamoId: String? = null,
    ): Result<EmployeePayment>
}
