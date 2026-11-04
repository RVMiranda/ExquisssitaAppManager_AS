package com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.repository.DuplicatePaymentException
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterPayrollPaymentUseCaseTest {
    @Test
    fun rejectsPeriodThatDoesNotStartOnMonday() = runBlocking {
        val repository = FakePayrollRepository()
        val result = RegisterPayrollPaymentUseCase(repository)("e1", "2026-10-07")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repository.calls)
    }

    @Test
    fun returnsDuplicatePaymentFailure() = runBlocking {
        val repository = FakePayrollRepository().apply { failure = DuplicatePaymentException() }
        val result = RegisterPayrollPaymentUseCase(repository)("e1", "2026-10-05")
        assertTrue(result.exceptionOrNull() is DuplicatePaymentException)
        assertEquals(1, repository.calls)
    }

    private class FakePayrollRepository : PayrollRepository {
        var calls = 0
        var failure: Throwable? = null
        override suspend fun getPaymentsForPeriod(periodoInicio: String) = Result.success(emptyList<EmployeePayment>())
        override suspend fun getPaymentHistoryForEmployee(empleadoId: String) = Result.success(emptyList<EmployeePayment>())
        override suspend fun registerWeeklyPayment(
            empleadoId: String,
            periodoInicio: String,
            fechaPago: String?,
            notas: String?,
            descuentoPrestamoCents: Long,
            prestamoId: String?,
        ): Result<EmployeePayment> {
            calls++
            return failure?.let { Result.failure(it) } ?: Result.failure(UnsupportedOperationException())
        }
    }
}
