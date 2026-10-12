package com.mirandadevsource.exquisssitaappmanager.features.tables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TablesViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TablesUiState())
    val uiState: StateFlow<TablesUiState> = _uiState.asStateFlow()

    private var selectedTableOrdersJob: Job? = null

    init {
        observeTables()
        refreshTables(isInitial = true)
    }

    private fun observeTables() {
        viewModelScope.launch {
            tableRepository.observeTables().collect { tablesList ->
                _uiState.update { current ->
                    // Si hay una mesa seleccionada en el diálogo, actualizar su referencia
                    val updatedSelected = current.selectedTable?.let { sel ->
                        tablesList.firstOrNull { it.id == sel.id } ?: sel
                    }
                    current.copy(
                        rawTables = tablesList,
                        selectedTable = updatedSelected,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun refreshTables(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { current ->
                if (isInitial) current.copy(isLoading = true, errorMessage = null)
                else current.copy(isRefreshing = true, errorMessage = null)
            }

            // Refrescar órdenes remotas primero para que Room tenga las órdenes actualizadas al derivar ocupación
            orderRepository.refreshOrders()
            val result = tableRepository.refreshTables()

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChange(filter: TableStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(searchQuery = "", statusFilter = TableStatusFilter.TODAS) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // --- Diálogo Crear Mesa ---

    fun openCreateTableDialog() {
        _uiState.update {
            it.copy(
                isCreateTableDialogOpen = true,
                newTableNumber = "",
                createTableError = null,
                isSavingTable = false,
            )
        }
    }

    fun dismissCreateTableDialog() {
        _uiState.update { it.copy(isCreateTableDialogOpen = false, createTableError = null) }
    }

    fun onNewTableNumberChange(number: String) {
        _uiState.update { it.copy(newTableNumber = number, createTableError = null) }
    }

    fun createTable() {
        val numberTrimmed = _uiState.value.newTableNumber.trim()
        if (numberTrimmed.isBlank()) {
            _uiState.update { it.copy(createTableError = "El número o nombre de la mesa no puede estar vacío.") }
            return
        }

        val existing = _uiState.value.rawTables.any { it.number.equals(numberTrimmed, ignoreCase = true) }
        if (existing) {
            _uiState.update { it.copy(createTableError = "Ya existe una mesa con el nombre o número '$numberTrimmed'.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTable = true, createTableError = null) }

            val result = tableRepository.createTable(numberTrimmed)
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isCreateTableDialogOpen = false,
                        isSavingTable = false,
                        userMessage = "Mesa '$numberTrimmed' registrada exitosamente.",
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isSavingTable = false,
                        createTableError = error.message ?: "Error al registrar la mesa.",
                    )
                }
            }
        }
    }

    // --- Diálogo Detalle de Mesa ---

    fun onTableClick(table: Table) {
        selectedTableOrdersJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTable = table,
                isTableDetailDialogOpen = true,
                isLoadingOrders = true,
                tableOrders = emptyList(),
                detailActionError = null,
            )
        }

        selectedTableOrdersJob = viewModelScope.launch {
            orderRepository.observeOrdersForTable(table.id).collect { ordersList ->
                _uiState.update { current ->
                    current.copy(
                        tableOrders = ordersList,
                        isLoadingOrders = false,
                    )
                }
            }
        }
    }

    fun dismissTableDetailDialog() {
        selectedTableOrdersJob?.cancel()
        _uiState.update {
            it.copy(
                isTableDetailDialogOpen = false,
                selectedTable = null,
                tableOrders = emptyList(),
                detailActionError = null,
            )
        }
    }

    fun toggleReservation(table: Table) {
        if (table.status == TableStatus.OCCUPIED) {
            _uiState.update { it.copy(detailActionError = "No se puede reservar una mesa con órdenes abiertas.") }
            return
        }

        val shouldReserve = table.status != TableStatus.RESERVED

        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingReservation = true, detailActionError = null) }

            val result = tableRepository.setReservation(table.id, shouldReserve)
            result.onSuccess {
                val actionMessage = if (shouldReserve) "Mesa marcada como reservada." else "Reserva de la mesa liberada."
                _uiState.update { current ->
                    current.copy(
                        isTogglingReservation = false,
                        userMessage = actionMessage,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isTogglingReservation = false,
                        detailActionError = error.message ?: "Error al actualizar la reservación.",
                    )
                }
            }
        }
    }

    fun syncPendingCommands() {
        viewModelScope.launch {
            tableRepository.syncPendingCommands()
        }
    }
}
