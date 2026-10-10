package com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveEmployeeUseCaseTest {
    @Test
    fun rejectsNonPositiveWeeklySalaryWithoutCallingRepository() = runBlocking {
        val repository = FakeEmployeeRepository()
        val result = SaveEmployeeUseCase(repository)("Ana", "López", null, 0, 1)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun acceptsSundayAndTrimsFields() = runBlocking {
        val repository = FakeEmployeeRepository()
        val result = SaveEmployeeUseCase(repository)(" Ana ", " López ", " ", 120_000, 7)
        assertTrue(result.isSuccess)
        assertEquals("Ana", repository.lastName)
        assertEquals("López", repository.lastSurname)
        assertEquals(120_000L, repository.lastSalary)
        assertEquals(7, repository.lastPayday)
    }

    private class FakeEmployeeRepository : EmployeeRepository {
        var createCalls = 0
        var lastName = ""
        var lastSurname = ""
        var lastSalary = 0L
        var lastPayday = 0

        override suspend fun getEmployees(includeInactive: Boolean) = Result.success(emptyList<Employee>())
        override suspend fun getEmployeeById(id: String) = Result.failure<Employee>(UnsupportedOperationException())
        override suspend fun createEmployee(nombre: String, apellido: String, telefono: String?, salarioCents: Long, diaPago: Int): Result<Employee> {
            createCalls++
            lastName = nombre
            lastSurname = apellido
            lastSalary = salarioCents
            lastPayday = diaPago
            return Result.success(employee())
        }
        override suspend fun updateEmployee(id: String, nombre: String, apellido: String, telefono: String?, salarioCents: Long, diaPago: Int, activo: Boolean) = Result.success(employee())
        override suspend fun toggleEmployeeActive(id: String, active: Boolean) = Result.success(Unit)

        private fun employee() = Employee("e1", null, lastName, lastSurname, null, lastSalary, lastPayday, true, "", "")
    }
}
