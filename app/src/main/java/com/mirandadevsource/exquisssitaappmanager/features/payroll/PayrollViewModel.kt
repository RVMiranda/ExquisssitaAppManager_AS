package com.mirandadevsource.exquisssitaappmanager.features.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.GetWeeklyPayrollStatusUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.RegisterPayrollPaymentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val getWeeklyPayrollStatusUseCase: GetWeeklyPayrollStatusUseCase,
    private val registerPayrollPaymentUseCase: RegisterPayrollPaymentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayrollUiState())
    val uiState: StateFlow<PayrollUiState> = _uiState.asStateFlow()

    init {
        loadPayroll()
    }

    fun loadPayroll(date: LocalDate = _uiState.value.referenceDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, referenceDate = date, errorMessage = null) }
            getWeeklyPayrollStatusUseCase(referenceDate = date)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar la nómina de la semana.",
                        )
                    }
                }
        }
    }

    fun previousWeek() {
        val newDate = _uiState.value.referenceDate.minusWeeks(1)
        loadPayroll(newDate)
    }

    fun nextWeek() {
        val newDate = _uiState.value.referenceDate.plusWeeks(1)
        loadPayroll(newDate)
    }

    fun currentWeek() {
        loadPayroll(LocalDate.now())
    }

    fun openPaymentModal(item: WeeklyPayrollItem) {
        _uiState.update {
            it.copy(
                paymentModalItem = item,
                paymentDateInput = LocalDate.now().toString(),
                paymentNotesInput = "",
                errorMessage = null,
            )
        }
    }

    fun updatePaymentDate(date: String) {
        _uiState.update { it.copy(paymentDateInput = date) }
    }

    fun updatePaymentNotes(notes: String) {
        _uiState.update { it.copy(paymentNotesInput = notes) }
    }

    fun closePaymentModal() {
        _uiState.update { it.copy(paymentModalItem = null, isRegisteringPayment = false) }
    }

    fun confirmPayment() {
        val item = _uiState.value.paymentModalItem ?: return
        val weekStart = _uiState.value.weekStartMonday.toString()
        val payDate = _uiState.value.paymentDateInput.trim().ifBlank { null }
        val notes = _uiState.value.paymentNotesInput.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegisteringPayment = true, errorMessage = null) }
            registerPayrollPaymentUseCase(
                empleadoId = item.employee.id,
                periodoInicio = weekStart,
                fechaPago = payDate,
                notas = notes,
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isRegisteringPayment = false,
                        paymentModalItem = null,
                        successMessage = "Pago de ${item.employee.fullName} registrado correctamente.",
                    )
                }
                loadPayroll()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRegisteringPayment = false,
                        errorMessage = error.localizedMessage ?: "No se pudo registrar el pago de nómina.",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
