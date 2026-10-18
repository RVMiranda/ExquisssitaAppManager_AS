package com.mirandadevsource.exquisssitaappmanager.features.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class EmployeesUiTest {

    private val sampleEmployees = listOf(
        Employee(
            id = "emp-1",
            nombre = "Carlos",
            apellido = "Ramírez",
            telefono = "9611234567",
            salarioCents = 150000L,
            diaPago = 6,
            activo = true,
            userId = "usr-carlos",
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
        ),
        Employee(
            id = "emp-2",
            nombre = "Ana",
            apellido = "López",
            telefono = "9619876543",
            salarioCents = 120000L,
            diaPago = 6,
            activo = false,
            userId = null,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
        ),
        Employee(
            id = "emp-3",
            nombre = "Sofía",
            apellido = "Pérez",
            telefono = null,
            salarioCents = 180000L,
            diaPago = 5,
            activo = true,
            userId = "usr-manager",
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
        ),
    )

    @Test
    fun `initial state has correct default values`() {
        val state = EmployeesUiState()
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoadingPage)
        assertFalse(state.hasMore)
        assertNull(state.nextOffset)
        assertNull(state.pageFetchedAt)
        assertTrue(state.employees.isEmpty())
        assertEquals(EmployeeFilter.ACTIVOS, state.filter)
        assertNull(state.formData)
        assertFalse(state.isSaving)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
        assertNull(state.currentUserRole)
        assertNull(state.currentUserId)
    }

    @Test
    fun `filter applies correctly to employee list`() {
        val state = EmployeesUiState(employees = sampleEmployees)

        // ACTIVOS (default)
        assertEquals(listOf("emp-1", "emp-3"), state.filteredEmployees.map { it.id })

        // INACTIVOS
        val inactivos = state.copy(filter = EmployeeFilter.INACTIVOS)
        assertEquals(listOf("emp-2"), inactivos.filteredEmployees.map { it.id })

        // TODOS
        val todos = state.copy(filter = EmployeeFilter.TODOS)
        assertEquals(listOf("emp-1", "emp-2", "emp-3"), todos.filteredEmployees.map { it.id })
    }

    @Test
    fun `SEC-03 Admin role can edit salary of any employee including new`() {
        val adminState = EmployeesUiState(
            currentUserRole = UserRole.ADMIN,
            currentUserId = "usr-admin",
        )

        // New employee
        assertTrue(adminState.canUserEditSalary(null))

        // Floor employee (no user account)
        assertTrue(adminState.canUserEditSalary(sampleEmployees[1]))

        // Self or other manager
        assertTrue(adminState.canUserEditSalary(sampleEmployees[0]))
        assertTrue(adminState.canUserEditSalary(sampleEmployees[2]))
    }

    @Test
    fun `SEC-03 Gerente role cannot edit own salary but can edit floor staff`() {
        val gerenteState = EmployeesUiState(
            currentUserRole = UserRole.GERENTE,
            currentUserId = "usr-manager", // Matches emp-3
        )

        // Can create new staff
        assertTrue(gerenteState.canUserEditSalary(null))

        // Can edit floor staff without linked user account
        assertTrue(gerenteState.canUserEditSalary(sampleEmployees[1]))

        // Can edit other employee
        assertTrue(gerenteState.canUserEditSalary(sampleEmployees[0]))

        // CANNOT edit own salary (SEC-03 restriction)
        assertFalse(gerenteState.canUserEditSalary(sampleEmployees[2]))
    }

    @Test
    fun `SEC-03 Mesero or Cajero roles cannot edit any salary`() {
        val meseroState = EmployeesUiState(
            currentUserRole = UserRole.MESERO,
            currentUserId = "usr-mesero",
        )
        assertFalse(meseroState.canUserEditSalary(null))
        assertFalse(meseroState.canUserEditSalary(sampleEmployees[0]))
        assertFalse(meseroState.canUserEditSalary(sampleEmployees[1]))
    }

    @Test
    fun `EmployeeFormData correctly identifies editing mode`() {
        val createForm = EmployeeFormData()
        assertFalse(createForm.isEditing)

        val editForm = EmployeeFormData(employeeId = "emp-1")
        assertTrue(editForm.isEditing)
    }

    @Test
    fun `DataPage holds pagination contract correctly`() {
        val page = DataPage(
            items = sampleEmployees.take(2),
            nextOffset = 2,
            hasMore = true,
            scope = "employees",
            fetchedAt = Instant.now(),
        )

        assertEquals(2, page.items.size)
        assertEquals(2, page.nextOffset)
        assertTrue(page.hasMore)
        assertEquals("employees", page.scope)
        assertNotNull(page.fetchedAt)
    }
}
