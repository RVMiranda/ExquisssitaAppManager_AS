package com.mirandadevsource.exquisssitaappmanager.features.cash_register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.CloseCashRegisterUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.GetCashRegisterCutsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.GetCashRegisterDaySummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CashRegisterViewModel @Inject constructor(
    private val closeCashRegisterUseCase: CloseCashRegisterUseCase,
    private val getCashRegisterCutsUseCase: GetCashRegisterCutsUseCase,
    private val getCashRegisterDaySummaryUseCase: GetCashRegisterDaySummaryUseCase,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CashRegisterUiState(
            isLoading = true,
            isOnline = networkMonitor.isOnline(),
        )
    )
    val uiState: StateFlow<CashRegisterUiState> = _uiState.asStateFlow()

    init {
        // Observar rol de usuario en sesión
        viewModelScope.launch {
            authRepository.state.collect { authState ->
                val role = (authState as? AuthState.SignedIn)?.session?.role
                _uiState.update { it.copy(userRole = role) }
            }
        }

        // Observar órdenes reactivamente para alertar de comandas abiertas
        viewModelScope.launch {
            orderRepository.observeOrders().collect { orders ->
                val openCount = orders.count { it.status == OrderStatus.OPEN }
                _uiState.update { it.copy(openOrdersCount = openCount) }
            }
        }

        // Carga inicial
        loadCuts()
        refreshOrders()
    }

    fun loadCuts(
        date: String = _uiState.value.selectedDate,
        isPullToRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update {
                if (isPullToRefresh) {
                    it.copy(isRefreshing = true, errorMessage = null)
                } else {
                    it.copy(
                        isLoading = true,
                        selectedDate = date,
                        cuts = if (date != it.selectedDate) emptyList() else it.cuts,
                        daySummary = if (date != it.selectedDate) null else it.daySummary,
                        errorMessage = null,
                    )
                }
            }

            val cutsResult = getCashRegisterCutsUseCase(date)
            val summaryResult = getCashRegisterDaySummaryUseCase(date)

            cutsResult.onSuccess { cutsList ->
                val summary = summaryResult.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        selectedDate = date,
                        cuts = cutsList,
                        daySummary = summary,
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        selectedDate = date,
                        errorMessage = err.message ?: "Error al cargar los cortes de caja.",
                        isOnline = networkMonitor.isOnline(),
                    )
                }
            }
        }
    }

    fun onDateSelected(date: String) {
        if (date != _uiState.value.selectedDate) {
            loadCuts(date)
        }
    }

    fun selectToday() {
        val today = LocalDate.now(CashRegisterUiState.BUSINESS_ZONE).toString()
        onDateSelected(today)
    }

    fun onRefresh() {
        loadCuts(_uiState.value.selectedDate, isPullToRefresh = true)
        refreshOrders()
    }

    private fun refreshOrders() {
        viewModelScope.launch {
            orderRepository.refreshOrders()
        }
    }

    fun openConfirmCutDialog() {
        val state = _uiState.value
        if (!state.isToday) {
            _uiState.update { it.copy(errorMessage = "Solo se pueden realizar cortes para el día operativo en curso.") }
            return
        }
        if (!state.isOnline) {
            _uiState.update { it.copy(errorMessage = "Se requiere conexión a internet para realizar el corte de caja.") }
            return
        }
        if (state.openOrdersCount > 0) {
            _uiState.update { it.copy(errorMessage = "No se puede realizar el corte mientras haya comandas abiertas.") }
            return
        }
        if (state.maxCutsReached) {
            _uiState.update { it.copy(errorMessage = "Ya se alcanzó el límite máximo de cinco cortes permitidos para hoy.") }
            return
        }
        if (state.userRole != UserRole.ADMIN && state.userRole != UserRole.GERENTE) {
            _uiState.update { it.copy(errorMessage = "Solo admin o gerente activo puede realizar el corte de caja.") }
            return
        }
        _uiState.update { it.copy(showConfirmCutDialog = true, errorMessage = null) }
    }

    fun dismissConfirmCutDialog() {
        _uiState.update { it.copy(showConfirmCutDialog = false) }
    }

    fun executeCut() {
        val state = _uiState.value
        if (!state.isToday) {
            _uiState.update { it.copy(errorMessage = "Solo se pueden realizar cortes para el día operativo en curso.") }
            return
        }
        if (!state.isOnline) {
            _uiState.update { it.copy(errorMessage = "Se requiere conexión a internet para realizar el corte de caja.") }
            return
        }
        if (state.openOrdersCount > 0) {
            _uiState.update { it.copy(errorMessage = "No se puede realizar el corte mientras haya comandas abiertas.") }
            return
        }
        if (state.maxCutsReached) {
            _uiState.update { it.copy(errorMessage = "Ya se alcanzó el máximo de cinco cortes del día.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, showConfirmCutDialog = false, errorMessage = null) }
        viewModelScope.launch {
            closeCashRegisterUseCase()
                .onSuccess { newClosure ->
                    val today = LocalDate.now(CashRegisterUiState.BUSINESS_ZONE).toString()
                    loadCuts(today)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            successMessage = "Corte #${newClosure.cutNumber} realizado exitosamente.",
                            isOnline = true,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Error al realizar el corte de caja.",
                            isOnline = networkMonitor.isOnline(),
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
