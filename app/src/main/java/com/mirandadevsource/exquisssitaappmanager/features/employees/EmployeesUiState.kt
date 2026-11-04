package com.mirandadevsource.exquisssitaappmanager.features.employees

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import java.time.Instant

enum class EmployeeScreenTab(val title: String) {
    DIRECTORIO("Directorio"),
    PRESTAMOS("Prestamos"),
}

enum class EmployeeFilter(val displayName: String) {
    ACTIVOS("Activos"),
    TODOS("Todos"),
    INACTIVOS("Inactivos"),
}

enum class LoanFilter(val displayName: String) {
    TODOS("Todos"),
    PENDIENTES("Pendientes"),
    LIQUIDADOS("Liquidados"),
}

data class EmployeeFormData(
    val employeeId: String? = null,
    val userId: String? = null,
    val nombre: String = "",
    val apellido: String = "",
    val telefono: String = "",
    val salarioPesosText: String = "",
    val diaPago: Int = 6, // 1=Lunes..7=Domingo (Default: Sabado)
    val activo: Boolean = true,
    val canEditSalary: Boolean = true,
) {
    val isEditing: Boolean get() = employeeId != null
}

data class CreateLoanFormData(
    val empleadoId: String = "",
    val montoPesosText: String = "",
    val fechaText: String = "",
    val notasText: String = "",
)

data class AbonoLoanFormData(
    val loan: EmployeeLoan? = null,
    val montoPesosText: String = "",
    val notasText: String = "",
)

data class EmployeesUiState(
    val selectedTab: EmployeeScreenTab = EmployeeScreenTab.DIRECTORIO,
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
    // Modulo de Prestamos
    val loans: List<EmployeeLoan> = emptyList(),
    val isLoadingLoans: Boolean = false,
    val loanFilter: LoanFilter = LoanFilter.TODOS,
    val createLoanForm: CreateLoanFormData? = null,
    val isSavingLoan: Boolean = false,
    val loanFormError: String? = null,
    val abonoForm: AbonoLoanFormData? = null,
    val isSavingAbono: Boolean = false,
    val abonoFormError: String? = null,
) {
    val filteredEmployees: List<Employee>
        get() = when (filter) {
            EmployeeFilter.ACTIVOS -> employees.filter { it.activo }
            EmployeeFilter.INACTIVOS -> employees.filter { !it.activo }
            EmployeeFilter.TODOS -> employees
        }

    val filteredLoans: List<EmployeeLoan>
        get() = when (loanFilter) {
            LoanFilter.TODOS -> loans
            LoanFilter.PENDIENTES -> loans.filter { it.isPending }
            LoanFilter.LIQUIDADOS -> loans.filter { it.estado == LoanStatus.LIQUIDADO }
        }

    val pendingLoansCount: Int
        get() = loans.count { it.isPending }

    val totalPendingLoansCents: Long
        get() = loans.filter { it.isPending }.sumOf { it.saldoPendienteCentavos }

    val totalPrestadoCents: Long
        get() = loans.sumOf { it.montoCentavos }

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
