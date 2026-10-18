package com.mirandadevsource.exquisssitaappmanager.features.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees.GetEmployeesUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees.SaveEmployeeUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.employees.ToggleEmployeeActiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    private val getEmployeesUseCase: GetEmployeesUseCase,
    private val saveEmployeeUseCase: SaveEmployeeUseCase,
    private val toggleEmployeeActiveUseCase: ToggleEmployeeActiveUseCase,
    private val employeeRepository: EmployeeRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeesUiState())
    val uiState: StateFlow<EmployeesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collect { authState ->
                val session = (authState as? AuthState.SignedIn)?.session
                _uiState.update {
                    it.copy(
                        currentUserRole = session?.role,
                        currentUserId = session?.userId,
                    )
                }
            }
        }
        loadEmployees()
    }

    fun loadEmployees(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
                else it.copy(isLoading = true, errorMessage = null)
            }
            val includeInactive = _uiState.value.filter != EmployeeFilter.ACTIVOS
            val request = PageRequest(offset = 0, limit = 50)
            employeeRepository.getEmployeePage(includeInactive = includeInactive, page = request)
                .onSuccess { dataPage ->
                    val list = dataPage.items
                    val conflictedCount = list.count { it.syncState == "CONFLICT" }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            employees = list,
                            hasMore = dataPage.hasMore,
                            nextOffset = dataPage.nextOffset,
                            pageFetchedAt = dataPage.fetchedAt,
                            errorMessage = if (conflictedCount > 0) {
                                "$conflictedCount cambio(s) de empleado tienen conflicto de versión. No los vuelvas a guardar hasta revisar el dato vigente en Supabase."
                            } else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar la lista de empleados.",
                        )
                    }
                }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        val offset = state.nextOffset ?: return
        if (!state.hasMore || state.isLoadingPage || state.isLoading) return

        _uiState.update { it.copy(isLoadingPage = true) }
        viewModelScope.launch {
            val includeInactive = _uiState.value.filter != EmployeeFilter.ACTIVOS
            val request = PageRequest(offset = offset, limit = 50)
            employeeRepository.getEmployeePage(includeInactive = includeInactive, page = request)
                .onSuccess { dataPage ->
                    _uiState.update { current ->
                        val existingIds = current.employees.map { it.id }.toSet()
                        val newItems = dataPage.items.filterNot { existingIds.contains(it.id) }
                        current.copy(
                            isLoadingPage = false,
                            employees = current.employees + newItems,
                            hasMore = dataPage.hasMore,
                            nextOffset = dataPage.nextOffset,
                            pageFetchedAt = dataPage.fetchedAt ?: current.pageFetchedAt,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingPage = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar más empleados.",
                        )
                    }
                }
        }
    }

    fun setFilter(filter: EmployeeFilter) {
        _uiState.update { it.copy(filter = filter, nextOffset = null, hasMore = false) }
        loadEmployees()
    }

    fun openCreateForm() {
        _uiState.update {
            it.copy(
                formData = EmployeeFormData(canEditSalary = true),
                errorMessage = null,
            )
        }
    }

    fun openEditForm(employee: Employee) {
        val canEditSalary = _uiState.value.canUserEditSalary(employee)
        _uiState.update {
            it.copy(
                formData = EmployeeFormData(
                    employeeId = employee.id,
                    userId = employee.userId,
                    nombre = employee.nombre,
                    apellido = employee.apellido,
                    telefono = employee.telefono.orEmpty(),
                    salarioPesosText = employee.salarioPesos.toPlainString(),
                    diaPago = employee.diaPago,
                    activo = employee.activo,
                    canEditSalary = canEditSalary,
                ),
                errorMessage = null,
            )
        }
    }

    fun updateFormData(transform: (EmployeeFormData) -> EmployeeFormData) {
        _uiState.update { state ->
            val currentForm = state.formData ?: EmployeeFormData()
            state.copy(formData = transform(currentForm))
        }
    }

    fun closeForm() {
        _uiState.update { it.copy(formData = null) }
    }

    fun saveEmployee() {
        val form = _uiState.value.formData ?: return
        val cleanName = form.nombre.trim()
        val cleanSurname = form.apellido.trim()
        if (cleanName.isBlank() || cleanSurname.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre y apellido son obligatorios.") }
            return
        }

        val existingEmployee = form.employeeId?.let { id -> _uiState.value.employees.firstOrNull { it.id == id } }
        val cents = if (!form.canEditSalary && existingEmployee != null) {
            existingEmployee.salarioCents
        } else {
            try {
                val normalizedSalary = form.salarioPesosText.trim().replace("$", "").replace(",", "")
                val decimal = BigDecimal(normalizedSalary)
                if (decimal <= BigDecimal.ZERO) {
                    _uiState.update { it.copy(errorMessage = "La cuota semanal debe ser mayor que cero.") }
                    return
                }
                decimal.movePointRight(2).longValueExact()
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Ingresa un salario válido en pesos (ej. 1200.00).") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            saveEmployeeUseCase(
                nombre = cleanName,
                apellido = cleanSurname,
                telefono = form.telefono.trim().ifBlank { null },
                salarioCents = cents,
                diaPago = form.diaPago,
                employeeId = form.employeeId,
                activo = form.activo,
            ).onSuccess { employee ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        formData = null,
                        successMessage = if (employee.syncState == "PENDING_UPDATE" || employee.syncState == "SYNC_ERROR") {
                            "Cambio guardado localmente y pendiente de sincronizar."
                        } else {
                            "Empleado guardado exitosamente."
                        },
                    )
                }
                loadEmployees()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.localizedMessage ?: "Error al guardar el empleado.",
                    )
                }
            }
        }
    }

    fun toggleActive(employee: Employee) {
        val newStatus = !employee.activo
        viewModelScope.launch {
            toggleEmployeeActiveUseCase(employee.id, newStatus)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            successMessage = if (newStatus) {
                                "Empleado reactivado correctamente."
                            } else {
                                "Empleado desactivado (baja lógica)."
                            },
                        )
                    }
                    loadEmployees()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.localizedMessage ?: "No se pudo actualizar el estado del empleado.",
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
