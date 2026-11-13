package com.mirandadevsource.exquisssitaappmanager.features.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.DeleteExpenseTemplateUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.DeleteExpenseUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.GetDailyProfitSummaryUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.GetExpenseTemplatesUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.GetExpensesUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.SaveExpenseTemplateUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.expenses.SaveExpenseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val getExpensesUseCase: GetExpensesUseCase,
    private val saveExpenseUseCase: SaveExpenseUseCase,
    private val deleteExpenseUseCase: DeleteExpenseUseCase,
    private val getExpenseTemplatesUseCase: GetExpenseTemplatesUseCase,
    private val saveExpenseTemplateUseCase: SaveExpenseTemplateUseCase,
    private val deleteExpenseTemplateUseCase: DeleteExpenseTemplateUseCase,
    private val getDailyProfitSummaryUseCase: GetDailyProfitSummaryUseCase,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExpensesUiState(
            isLoading = true,
            isOnline = networkMonitor.isOnline(),
        )
    )
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    private var expensesObservationJob: Job? = null

    init {
        // Observar estado de autenticación y rol
        viewModelScope.launch {
            authRepository.state.collect { authState ->
                val role = (authState as? AuthState.SignedIn)?.session?.role
                _uiState.update { it.copy(userRole = role) }
            }
        }

        // Observar conectividad
        viewModelScope.launch {
            networkMonitor.online.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }

        // Observar plantillas de gastos activas (para selección en formulario de gastos)
        viewModelScope.launch {
            getExpenseTemplatesUseCase.getFlow().collect { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
        }

        // Observar catálogo completo de plantillas (activas e inactivas) para administración
        viewModelScope.launch {
            getExpenseTemplatesUseCase.getAllFlow().collect { allTemplates ->
                _uiState.update { it.copy(allTemplates = allTemplates) }
            }
        }

        observeExpensesForDate(_uiState.value.selectedDate)
        refreshAll(_uiState.value.selectedDate, isInitial = true)
    }

    fun selectTab(tab: ExpenseViewTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
    }

    private fun observeExpensesForDate(date: String) {
        expensesObservationJob?.cancel()
        expensesObservationJob = viewModelScope.launch {
            getExpensesUseCase.getFlow(date).collect { list ->
                _uiState.update { it.copy(expenses = list) }
            }
        }
    }

    fun onDateSelected(newDate: String) {
        if (newDate == _uiState.value.selectedDate) return
        _uiState.update {
            it.copy(
                selectedDate = newDate,
                isLoading = true,
                errorMessage = null,
                profitSummary = null,
            )
        }
        observeExpensesForDate(newDate)
        refreshAll(newDate, isInitial = false)
    }

    fun selectToday() {
        val today = LocalDate.now(ExpensesUiState.BUSINESS_ZONE).toString()
        onDateSelected(today)
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    fun onRefresh() {
        refreshAll(_uiState.value.selectedDate, isInitial = false)
    }

    private fun refreshAll(date: String, isInitial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isRefreshing = true, errorMessage = null)
            }

            try {
                getExpensesUseCase.refresh(date)
                getExpenseTemplatesUseCase.refresh()

                val summaryResult = getDailyProfitSummaryUseCase(date)
                summaryResult.onSuccess { summary ->
                    _uiState.update { it.copy(profitSummary = summary) }
                }
            } catch (err: Exception) {
                _uiState.update { it.copy(errorMessage = err.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingExpense = null,
                errorMessage = null,
            )
        }
    }

    fun openEditDialog(expense: Expense) {
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingExpense = expense,
                errorMessage = null,
            )
        }
    }

    fun closeFormDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = false,
                editingExpense = null,
            )
        }
    }

    fun saveExpense(
        concept: String,
        category: String,
        amountCents: Long,
        quantity: Double?,
        unitPriceCents: Long?,
        paymentMethod: ExpensePaymentMethod,
        templateId: String?,
        notes: String?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            val currentEdit = _uiState.value.editingExpense
            val targetDate = _uiState.value.selectedDate

            val expenseToSave = Expense(
                localId = currentEdit?.localId ?: UUID.randomUUID().toString(),
                remoteId = currentEdit?.remoteId,
                date = currentEdit?.date ?: targetDate,
                concept = concept,
                category = category,
                amountCents = amountCents,
                quantity = quantity,
                unitPriceCents = unitPriceCents,
                paymentMethod = paymentMethod,
                templateId = templateId,
                notes = notes,
                registeredBy = currentEdit?.registeredBy,
            )

            val result = saveExpenseUseCase(expenseToSave)
            result.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isFormDialogOpen = false,
                        editingExpense = null,
                        successMessage = if (currentEdit == null) "Gasto registrado correctamente." else "Gasto actualizado.",
                    )
                }
                // Actualizar resumen de rentabilidad
                refreshProfitSummary(targetDate)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Error al guardar el gasto.",
                    )
                }
            }
        }
    }

    fun requestDelete(expense: Expense) {
        _uiState.update { it.copy(expenseToDelete = expense) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(expenseToDelete = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.expenseToDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(expenseToDelete = null, isLoading = true) }
            val result = deleteExpenseUseCase(target.localId)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Gasto eliminado.",
                    )
                }
                refreshProfitSummary(_uiState.value.selectedDate)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "No se pudo eliminar el gasto.",
                    )
                }
            }
        }
    }

    // --- Gestión de Plantillas de Gastos Frecuentes ---

    fun setTemplateCategoryFilter(category: String?) {
        _uiState.update { it.copy(selectedTemplateCategoryFilter = category) }
    }

    fun openCreateTemplateDialog() {
        _uiState.update {
            it.copy(
                isTemplateDialogOpen = true,
                editingTemplate = null,
                errorMessage = null,
            )
        }
    }

    fun openEditTemplateDialog(template: ExpenseTemplate) {
        _uiState.update {
            it.copy(
                isTemplateDialogOpen = true,
                editingTemplate = template,
                errorMessage = null,
            )
        }
    }

    fun closeTemplateDialog() {
        _uiState.update {
            it.copy(
                isTemplateDialogOpen = false,
                editingTemplate = null,
            )
        }
    }

    fun saveTemplate(
        name: String,
        category: String,
        unitPriceCents: Long,
        unitOfMeasure: String,
        isActive: Boolean,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTemplate = true, errorMessage = null) }
            val currentEdit = _uiState.value.editingTemplate

            val result = saveExpenseTemplateUseCase(
                id = currentEdit?.id,
                name = name,
                category = category,
                unitPriceCents = unitPriceCents,
                unitOfMeasure = unitOfMeasure,
                isActive = isActive,
            )

            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingTemplate = false,
                        isTemplateDialogOpen = false,
                        editingTemplate = null,
                        successMessage = if (currentEdit == null) "Plantilla creada correctamente." else "Plantilla actualizada.",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSavingTemplate = false,
                        errorMessage = err.message ?: "Error al guardar la plantilla.",
                    )
                }
            }
        }
    }

    fun requestDeleteTemplate(template: ExpenseTemplate) {
        _uiState.update { it.copy(templateToDelete = template) }
    }

    fun cancelDeleteTemplate() {
        _uiState.update { it.copy(templateToDelete = null) }
    }

    fun confirmDeleteTemplate() {
        val target = _uiState.value.templateToDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(templateToDelete = null, isLoading = true) }
            val result = deleteExpenseTemplateUseCase(target.id)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Plantilla eliminada.",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "No se pudo eliminar la plantilla.",
                    )
                }
            }
        }
    }

    fun toggleTemplateActive(template: ExpenseTemplate) {
        viewModelScope.launch {
            val newActive = !template.isActive
            val result = deleteExpenseTemplateUseCase.toggleActive(template.id, newActive)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        successMessage = if (newActive) "Plantilla activada en catálogo." else "Plantilla desactivada del catálogo.",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Error al actualizar estado de la plantilla.")
                }
            }
        }
    }

    private fun refreshProfitSummary(date: String) {
        viewModelScope.launch {
            val summaryResult = getDailyProfitSummaryUseCase(date)
            summaryResult.onSuccess { summary ->
                _uiState.update { it.copy(profitSummary = summary) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
