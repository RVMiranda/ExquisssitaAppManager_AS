package com.mirandadevsource.exquisssitaappmanager.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TransactionDao
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll.GetWeeklyPayrollStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val inventoryRepository: InventoryRepository,
    private val getWeeklyPayrollStatusUseCase: GetWeeklyPayrollStatusUseCase,
    private val cashRegisterRepository: CashRegisterRepository,
    private val transactionDao: TransactionDao,
    private val orderCommandDao: OrderCommandDao,
    private val inventoryCommandDao: InventoryCommandDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeReactiveData()
        refreshDashboard(isInitial = true)
    }

    private fun observeReactiveData() {
        // 1. Observar ocupación y disponibilidad de mesas
        viewModelScope.launch {
            tableRepository.observeTables().collect { tables ->
                val free = tables.count { it.status == TableStatus.FREE }
                _uiState.update { it.copy(freeTables = free, totalTables = tables.size) }
            }
        }

        // 2. Observar órdenes del día y órdenes vivas abiertas
        viewModelScope.launch {
            orderRepository.observeOrders().collect { orders ->
                val todayStr = LocalDate.now(MEXICO_ZONE).toString()
                val todayOrders = orders.filter { it.openedAt.startsWith(todayStr) }
                val openCount = orders.count { it.status == OrderStatus.OPEN }
                _uiState.update {
                    it.copy(
                        todayOrdersCount = todayOrders.size,
                        openOrdersCount = openCount,
                    )
                }
            }
        }

        // 3. Observar transacciones registradas de hoy para totalizar ventas
        viewModelScope.launch {
            transactionDao.observeAll().collect { txs ->
                val todayStr = LocalDate.now(MEXICO_ZONE).toString()
                val todayTxs = txs.filter { it.fecha.startsWith(todayStr) }
                val sumCents = todayTxs.sumOf { it.montoCentavos }
                _uiState.update { current ->
                    current.copy(
                        todaySalesCents = maxOf(current.todaySalesCents, sumCents),
                        todayPaymentsCount = todayTxs.size,
                    )
                }
            }
        }

        // 4. Observar comandos pendientes en colas de sincronización (Outbox)
        viewModelScope.launch {
            combine(
                orderCommandDao.observePendingCount(),
                inventoryCommandDao.observePendingCount(),
            ) { orderPending, invPending ->
                orderPending + invPending
            }.collect { totalPending ->
                _uiState.update { it.copy(pendingSyncCount = totalPending) }
            }
        }
    }

    fun refreshDashboard(isInitial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isRefreshing = true, errorMessage = null)
            }

            // Sincronizar mesas y órdenes desde el backend
            tableRepository.refreshTables()
            orderRepository.refreshOrders()

            // Consultar productos para alertas de inventario bajo
            inventoryRepository.getProducts(includeInactive = false)
                .onSuccess { products ->
                    val lowStock = products.filter {
                        it.controlaInventario && (it.stockActual ?: 0) <= (it.stockMinimo ?: 0)
                    }.map {
                        LowStockProductItem(
                            id = it.id,
                            name = it.nombre,
                            currentStock = it.stockActual ?: 0,
                            minStock = it.stockMinimo ?: 0,
                            category = it.categoria,
                        )
                    }
                    _uiState.update { it.copy(lowStockProducts = lowStock) }
                }

            // Consultar nómina de la semana para alertas de pagos de hoy
            val todayDate = LocalDate.now(MEXICO_ZONE)
            val todayDayOfWeek = todayDate.dayOfWeek.value
            getWeeklyPayrollStatusUseCase(todayDate)
                .onSuccess { summary ->
                    val pendingToday = summary.items.filter { item ->
                        item.employee.diaPago == todayDayOfWeek && !item.isPaid
                    }.map { item ->
                        EmployeePayItem(
                            id = item.employee.id,
                            name = item.employee.fullName,
                            wageCents = item.employee.salarioCents,
                            dayName = item.employee.diaPagoNombre,
                        )
                    }
                    _uiState.update { it.copy(employeesToPayToday = pendingToday) }
                }

            // Consultar resumen de cortes del día
            cashRegisterRepository.getDaySummary(todayDate.toString())
                .onSuccess { summary ->
                    if (summary.totalCents > 0L) {
                        _uiState.update { current ->
                            current.copy(
                                todaySalesCents = maxOf(current.todaySalesCents, summary.totalCents),
                            )
                        }
                    }
                }

            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    fun syncPendingData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, userMessage = null) }
            val tResult = tableRepository.syncPendingCommands()
            val oResult = orderRepository.syncPendingCommands()
            val pResult = paymentRepository.syncPendingPayments()
            val iResult = inventoryRepository.syncPendingCommands()
            val payrollResult = getWeeklyPayrollStatusUseCase(LocalDate.now(MEXICO_ZONE))

            if (tResult.isFailure || oResult.isFailure || pResult.isFailure || iResult.isFailure || payrollResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = "Algunas operaciones no pudieron sincronizarse. Verifica tu conexión.",
                    )
                }
            } else {
                refreshDashboard()
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        userMessage = "Sincronización completada exitosamente.",
                    )
                }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }

    private companion object {
        val MEXICO_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
