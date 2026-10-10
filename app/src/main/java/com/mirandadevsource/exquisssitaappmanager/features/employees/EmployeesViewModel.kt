package com.mirandadevsource.exquisssitaappmanager.features.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployeesUiState())
    val uiState: StateFlow<EmployeesUiState> = _uiState.asStateFlow()

    init {
        loadEmployees()
    }

    fun loadEmployees() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getEmployeesUseCase(includeInactive = true)
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, employees = list) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar la lista de empleados.",
                        )
                    }
                }
        }
    }

    fun setFilter(filter: EmployeeFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun openCreateForm() {
        _uiState.update {
            it.copy(
                formData = EmployeeFormData(),
                errorMessage = null,
            )
        }
    }

    fun openEditForm(employee: Employee) {
        _uiState.update {
            it.copy(
                formData = EmployeeFormData(
                    employeeId = employee.id,
                    nombre = employee.nombre,
                    apellido = employee.apellido,
                    telefono = employee.telefono.orEmpty(),
                    salarioPesosText = employee.salarioPesos.toPlainString(),
                    diaPago = employee.diaPago,
                    activo = employee.activo,
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

        val cents = try {
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
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        formData = null,
                        successMessage = "Empleado guardado exitosamente.",
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
