package com.mirandadevsource.exquisssitaappmanager.features.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import java.time.Instant

enum class EmployeeFilter(val displayName: String) {
    ACTIVOS("Activos"),
    TODOS("Todos"),
    INACTIVOS("Inactivos"),
}

data class EmployeeFormData(
    val employeeId: String? = null,
    val userId: String? = null,
    val nombre: String = "",
    val apellido: String = "",
    val telefono: String = "",
    val salarioPesosText: String = "",
    val diaPago: Int = 6, // 1=Lunes..7=Domingo (Default: Sábado)
    val activo: Boolean = true,
    val canEditSalary: Boolean = true,
) {
    val isEditing: Boolean get() = employeeId != null
}

data class EmployeesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingPage: Boolean = false,
    val hasMore: Boolean = false,
    val nextOffset: Int? = null,
    val pageFetchedAt: Instant? = null,
    val employees: List<Employee> = emptyList(),
    val filter: EmployeeFilter = EmployeeFilter.ACTIVOS,
    val formData: EmployeeFormData? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentUserRole: UserRole? = null,
    val currentUserId: String? = null,
) {
    val filteredEmployees: List<Employee>
        get() = when (filter) {
            EmployeeFilter.ACTIVOS -> employees.filter { it.activo }
            EmployeeFilter.INACTIVOS -> employees.filter { !it.activo }
            EmployeeFilter.TODOS -> employees
        }

    fun canUserEditSalary(target: Employee?): Boolean {
        if (target == null) return currentUserRole == UserRole.ADMIN || currentUserRole == UserRole.GERENTE
        if (currentUserRole == UserRole.ADMIN) return true
        if (currentUserRole == UserRole.GERENTE) {
            // Gerente no puede editar su propio salario
            if (!target.userId.isNullOrBlank() && target.userId == currentUserId) return false
            return true
        }
        return false
    }
}
