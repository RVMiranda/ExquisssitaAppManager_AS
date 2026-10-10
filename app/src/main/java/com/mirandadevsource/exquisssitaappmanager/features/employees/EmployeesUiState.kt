package com.mirandadevsource.exquisssitaappmanager.features.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee

enum class EmployeeFilter(val displayName: String) {
    ACTIVOS("Activos"),
    TODOS("Todos"),
    INACTIVOS("Inactivos"),
}

data class EmployeeFormData(
    val employeeId: String? = null,
    val nombre: String = "",
    val apellido: String = "",
    val telefono: String = "",
    val salarioPesosText: String = "",
    val diaPago: Int = 6, // 1=Lunes..7=Domingo (Default: Sábado)
    val activo: Boolean = true,
) {
    val isEditing: Boolean get() = employeeId != null
}

data class EmployeesUiState(
    val isLoading: Boolean = false,
    val employees: List<Employee> = emptyList(),
    val filter: EmployeeFilter = EmployeeFilter.ACTIVOS,
    val formData: EmployeeFormData? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val filteredEmployees: List<Employee>
        get() = when (filter) {
            EmployeeFilter.ACTIVOS -> employees.filter { it.activo }
            EmployeeFilter.INACTIVOS -> employees.filter { !it.activo }
            EmployeeFilter.TODOS -> employees
        }
}
