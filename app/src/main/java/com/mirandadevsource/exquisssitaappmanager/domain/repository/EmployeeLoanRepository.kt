package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanPayment

interface EmployeeLoanRepository {
    suspend fun getAllLoans(): Result<List<EmployeeLoan>>
    suspend fun getLoansForEmployee(empleadoId: String): Result<List<EmployeeLoan>>
    suspend fun getLoanPayments(prestamoId: String): Result<List<LoanPayment>>
    suspend fun createLoan(
        empleadoId: String,
        montoCentavos: Long,
        fecha: String? = null,
        notas: String? = null,
    ): Result<EmployeeLoan>
    suspend fun registerPayment(
        prestamoId: String,
        montoCentavos: Long,
        tipo: String = "pago_directo",
        notas: String? = null,
    ): Result<EmployeeLoan>
}
