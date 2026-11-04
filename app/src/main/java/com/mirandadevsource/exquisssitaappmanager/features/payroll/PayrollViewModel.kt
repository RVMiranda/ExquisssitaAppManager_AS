package com.mirandadevsource.exquisssitaappmanager.features.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.GetWeeklyPayrollStatusUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.RegisterPayrollPaymentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val getWeeklyPayrollStatusUseCase: GetWeeklyPayrollStatusUseCase,
    private val registerPayrollPaymentUseCase: RegisterPayrollPaymentUseCase,
    private val payrollRepository: PayrollRepository,
    private val employeeLoanRepository: EmployeeLoanRepository,
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
                            errorMessage = error.localizedMessage ?: "Error al cargar la nomina de la semana.",
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
                activeLoanForPayment = null,
                loanDeductionPesosText = "",
                isLoadingLoanForPayment = true,
                loanDeductionError = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            employeeLoanRepository.getLoansForEmployee(item.employee.id)
                .onSuccess { loans ->
                    val activeLoan = loans.firstOrNull { it.isPending }
                    _uiState.update { current ->
                        current.copy(
                            isLoadingLoanForPayment = false,
                            activeLoanForPayment = activeLoan,
                            loanDeductionPesosText = "",
                            loanDeductionError = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingLoanForPayment = false) }
                }
        }
    }

    fun updatePaymentDate(date: String) {
        _uiState.update { it.copy(paymentDateInput = date) }
    }

    fun updatePaymentNotes(notes: String) {
        _uiState.update { it.copy(paymentNotesInput = notes) }
    }

    fun updateLoanDeduction(amountText: String) {
        _uiState.update { state ->
            val activeLoan = state.activeLoanForPayment
            var error: String? = null
            if (amountText.isNotBlank() && activeLoan != null) {
                try {
                    val normalized = amountText.trim().replace("$", "").replace(",", "")
                    val dec = BigDecimal(normalized)
                    val cents = dec.movePointRight(2).longValueExact()
                    val salaryCents = state.paymentModalItem?.employee?.salarioCents ?: 0L
                    if (cents < 0L) {
                        error = "El descuento no puede ser negativo."
                    } else if (cents > activeLoan.saldoPendienteCentavos) {
                        error = "El descuento no puede exceder el saldo pendiente ($${activeLoan.saldoPendientePesos})."
                    } else if (cents > salaryCents) {
                        error = "El descuento no puede ser mayor al sueldo semanal."
                    }
                } catch (_: Exception) {
                    error = "Monto de descuento no valido."
                }
            }
            state.copy(loanDeductionPesosText = amountText, loanDeductionError = error)
        }
    }

    fun applyFullLoanDeduction() {
        val state = _uiState.value
        val loan = state.activeLoanForPayment ?: return
        val salaryCents = state.paymentModalItem?.employee?.salarioCents ?: 0L
        val maxDeductibleCents = minOf(loan.saldoPendienteCentavos, salaryCents)
        val pesos = BigDecimal.valueOf(maxDeductibleCents, 2).toPlainString()
        updateLoanDeduction(pesos)
    }

    fun closePaymentModal() {
        _uiState.update {
            it.copy(
                paymentModalItem = null,
                isRegisteringPayment = false,
                activeLoanForPayment = null,
                loanDeductionPesosText = "",
                loanDeductionError = null,
            )
        }
    }

    fun confirmPayment() {
        val item = _uiState.value.paymentModalItem ?: return
        val state = _uiState.value
        if (state.loanDeductionError != null) return

        val weekStart = state.weekStartMonday.toString()
        val payDate = state.paymentDateInput.trim().ifBlank { null }
        val notes = state.paymentNotesInput.trim().ifBlank { null }
        val deductionCents = state.deductionCents
        val loanId = if (deductionCents > 0L) state.activeLoanForPayment?.id else null

        viewModelScope.launch {
            _uiState.update { it.copy(isRegisteringPayment = true, errorMessage = null) }
            registerPayrollPaymentUseCase(
                empleadoId = item.employee.id,
                periodoInicio = weekStart,
                fechaPago = payDate,
                notas = notes,
                descuentoPrestamoCents = deductionCents,
                prestamoId = loanId,
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isRegisteringPayment = false,
                        paymentModalItem = null,
                        activeLoanForPayment = null,
                        loanDeductionPesosText = "",
                        loanDeductionError = null,
                        successMessage = "Pago de ${item.employee.fullName} registrado correctamente.",
                    )
                }
                loadPayroll()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRegisteringPayment = false,
                        errorMessage = error.localizedMessage ?: "No se pudo registrar el pago de nomina.",
                    )
                }
            }
        }
    }

    fun openHistoryModal(item: WeeklyPayrollItem) {
        _uiState.update {
            it.copy(
                viewingHistoryEmployee = item,
                historyPayments = emptyList(),
                isLoadingHistory = true,
                isLoadingHistoryPage = false,
                historyHasMore = false,
                historyNextOffset = null,
                historyFetchedAt = null,
                historyError = null,
            )
        }
        loadHistory(item.employee.id, offset = 0)
    }

    fun closeHistoryModal() {
        _uiState.update {
            it.copy(
                viewingHistoryEmployee = null,
                historyPayments = emptyList(),
                isLoadingHistory = false,
                isLoadingHistoryPage = false,
                historyHasMore = false,
                historyNextOffset = null,
                historyError = null,
            )
        }
    }

    fun loadNextHistoryPage() {
        val state = _uiState.value
        val employee = state.viewingHistoryEmployee ?: return
        val offset = state.historyNextOffset ?: return
        if (!state.historyHasMore || state.isLoadingHistoryPage || state.isLoadingHistory) return

        _uiState.update { it.copy(isLoadingHistoryPage = true) }
        viewModelScope.launch {
            val request = PageRequest(offset = offset, limit = 50)
            payrollRepository.getPaymentHistoryPage(employee.employee.id, request)
                .onSuccess { dataPage ->
                    _uiState.update { current ->
                        val existingIds = current.historyPayments.map { it.id }.toSet()
                        val newItems = dataPage.items.filterNot { existingIds.contains(it.id) }
                        current.copy(
                            isLoadingHistoryPage = false,
                            historyPayments = current.historyPayments + newItems,
                            historyHasMore = dataPage.hasMore,
                            historyNextOffset = dataPage.nextOffset,
                            historyFetchedAt = dataPage.fetchedAt ?: current.historyFetchedAt,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistoryPage = false,
                            historyError = error.localizedMessage ?: "Error al cargar mas historial.",
                        )
                    }
                }
        }
    }

    private fun loadHistory(employeeId: String, offset: Int) {
        viewModelScope.launch {
            val request = PageRequest(offset = offset, limit = 50)
            payrollRepository.getPaymentHistoryPage(employeeId, request)
                .onSuccess { dataPage ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            historyPayments = dataPage.items,
                            historyHasMore = dataPage.hasMore,
                            historyNextOffset = dataPage.nextOffset,
                            historyFetchedAt = dataPage.fetchedAt,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            historyError = error.localizedMessage ?: "Error al cargar el historial de pagos.",
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
