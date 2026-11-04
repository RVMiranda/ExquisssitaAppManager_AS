package com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GetWeeklyPayrollStatusUseCaseTest {
    @Test
    fun usesMondayAndCalculatesPaidPendingAndTotalInCents() = runBlocking {
        val paidEmployee = employee("e1", 120_000)
        val unpaidEmployee = employee("e2", 10_000)
        val repository = FakePayrollRepository(listOf(payment("e1", 120_000)))
        val useCase = GetWeeklyPayrollStatusUseCase(
            FakeEmployeeRepository(listOf(paidEmployee, unpaidEmployee)), repository,
        )

        val summary = useCase(LocalDate.parse("2026-10-07")).getOrThrow()

        assertEquals("2026-10-05", summary.periodoInicio)
        assertEquals(120_000L, summary.pagadoCents)
        assertEquals(10_000L, summary.pendienteCents)
        assertEquals(130_000L, summary.totalCents)
        assertTrue(summary.items.first().isPaid)
        assertFalse(summary.items.last().isPaid)
        assertEquals("2026-10-05", repository.requestedPeriod)
    }

    private class FakeEmployeeRepository(private val employees: List<Employee>) : EmployeeRepository {
        override suspend fun getEmployees(includeInactive: Boolean) = Result.success(employees)
        override suspend fun getEmployeeById(id: String) = Result.success(employees.first { it.id == id })
        override suspend fun createEmployee(nombre: String, apellido: String, telefono: String?, salarioCents: Long, diaPago: Int) = Result.failure<Employee>(UnsupportedOperationException())
        override suspend fun updateEmployee(id: String, nombre: String, apellido: String, telefono: String?, salarioCents: Long, diaPago: Int, activo: Boolean) = Result.failure<Employee>(UnsupportedOperationException())
        override suspend fun toggleEmployeeActive(id: String, active: Boolean) = Result.success(Unit)
    }

    private class FakePayrollRepository(private val payments: List<EmployeePayment>) : PayrollRepository {
        var requestedPeriod: String? = null
        override suspend fun getPaymentsForPeriod(periodoInicio: String): Result<List<EmployeePayment>> {
            requestedPeriod = periodoInicio
            return Result.success(payments)
        }
        override suspend fun getPaymentHistoryForEmployee(empleadoId: String) = Result.success(emptyList<EmployeePayment>())
        override suspend fun registerWeeklyPayment(
            empleadoId: String,
            periodoInicio: String,
            fechaPago: String?,
            notas: String?,
            descuentoPrestamoCents: Long,
            prestamoId: String?,
        ) = Result.failure<EmployeePayment>(UnsupportedOperationException())
    }

    companion object {
        private fun employee(id: String, salary: Long) = Employee(id, null, id, "Apellido", null, salary, 1, true, "", "")
        private fun payment(employeeId: String, amount: Long) = EmployeePayment("p1", employeeId, amount, "2026-10-07", "2026-10-05", "2026-10-07", null, "admin", "", 1)
    }
}
