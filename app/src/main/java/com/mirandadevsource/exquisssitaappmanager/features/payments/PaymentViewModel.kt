package com.mirandadevsource.exquisssitaappmanager.features.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentAllocation
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var currentOrderId: String? = null
    private var detailsJob: Job? = null
    private var paymentsJob: Job? = null
    private var tableJob: Job? = null

    init {
        // Cargar catálogo de productos para mapear nombres en pantalla
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            inventoryRepository.getProducts(includeInactive = true)
                .onSuccess { products ->
                    _uiState.update { it.copy(productMap = products.associateBy { p -> p.id }) }
                }
        }
    }

    /**
     * Inicializa la vista de cobro con una orden específica.
     */
    fun loadOrder(order: Order) {
        currentOrderId = order.id
        detailsJob?.cancel()
        paymentsJob?.cancel()
        tableJob?.cancel()

        _uiState.update {
            it.copy(
                order = order,
                splitMode = SplitMode.FULL,
                selectedMethod = PaymentMethod.CASH,
                cashReceivedInput = "",
                errorMessage = null,
                successMessage = null,
                isSubmitting = false,
            )
        }

        // Cargar mesa asociada si corresponde
        if (order.tableId != null) {
            tableJob = viewModelScope.launch {
                tableRepository.observeTables().collect { tables ->
                    val foundTable = tables.firstOrNull { it.id == order.tableId }
                    _uiState.update { it.copy(table = foundTable) }
                }
            }
        } else {
            _uiState.update { it.copy(table = null) }
        }

        // Observar partidas de la comanda
        detailsJob = orderRepository.observeOrderDetails(order.id)
            .onEach { details ->
                _uiState.update { state ->
                    val pending = details.filter { it.paymentStatus == PaymentLineStatus.PENDING }
                    // Mantener o inicializar selección de artículos
                    val currentSelection = state.selectedItemQuantities.toMutableMap()
                    pending.forEach { line ->
                        if (!currentSelection.containsKey(line.id)) {
                            currentSelection[line.id] = line.quantity
                        } else {
                            val current = currentSelection[line.id] ?: 0
                            currentSelection[line.id] = current.coerceIn(0, line.quantity)
                        }
                    }
                    state.copy(
                        details = details,
                        selectedItemQuantities = currentSelection,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Observar pagos históricos de la comanda
        paymentsJob = paymentRepository.observePayments(order.id)
            .onEach { payments ->
                _uiState.update { it.copy(payments = payments) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Alterna el modo de cobro.
     */
    fun onSplitModeSelected(mode: SplitMode) {
        _uiState.update { state ->
            val updatedSelection = if (mode == SplitMode.BY_ITEMS && state.selectedItemQuantities.values.all { it == 0 }) {
                // Al entrar a por artículos por primera vez, sugerir 1 unidad por renglón
                state.pendingDetails.associate { it.id to 1 }
            } else {
                state.selectedItemQuantities
            }
            state.copy(
                splitMode = mode,
                selectedItemQuantities = updatedSelection,
                errorMessage = null,
            )
        }
    }

    /**
     * Cambia el método de pago seleccionado.
     */
    fun onPaymentMethodSelected(method: PaymentMethod) {
        _uiState.update { it.copy(selectedMethod = method, errorMessage = null) }
    }

    /**
     * Ajusta la cantidad seleccionada para un artículo en modo Por Partidas.
     */
    fun onItemQuantityChanged(detailId: String, delta: Int) {
        _uiState.update { state ->
            val line = state.details.firstOrNull { it.id == detailId } ?: return@update state
            val currentQty = state.selectedItemQuantities[detailId] ?: 0
            val newQty = (currentQty + delta).coerceIn(0, line.quantity)
            val updatedMap = state.selectedItemQuantities.toMutableMap()
            updatedMap[detailId] = newQty
            state.copy(selectedItemQuantities = updatedMap)
        }
    }

    /**
     * Selecciona todas las unidades pendientes en modo Por Partidas.
     */
    fun onSelectAllItems() {
        _uiState.update { state ->
            val allMax = state.pendingDetails.associate { it.id to it.quantity }
            state.copy(selectedItemQuantities = allMax)
        }
    }

    /**
     * Limpia la selección de artículos a 0 unidades.
     */
    fun onClearItemSelection() {
        _uiState.update { state ->
            val cleared = state.pendingDetails.associate { it.id to 0 }
            state.copy(selectedItemQuantities = cleared)
        }
    }

    /**
     * Cambia el número de personas para división equitativa.
     */
    fun onSplitCountChanged(delta: Int) {
        _uiState.update { state ->
            val newCount = (state.splitCount + delta).coerceIn(2, 20)
            state.copy(splitCount = newCount)
        }
    }

    /**
     * Actualiza el valor del efectivo recibido para calcular cambio.
     */
    fun onCashReceivedInputChanged(input: String) {
        _uiState.update { it.copy(cashReceivedInput = input) }
    }

    /**
     * Limpia mensajes informativos o de error.
     */
    fun dismissUserMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    /**
     * Ejecuta el cobro transaccional e idempotente en PaymentRepository.
     */
    fun processPayment(onPaymentSuccess: (() -> Unit)? = null) {
        val state = _uiState.value
        val order = state.order ?: return
        val totalPaymentCents = state.calculatedPaymentAmountCents

        if (!state.canSubmitPayment) {
            _uiState.update {
                it.copy(errorMessage = "El monto a cobrar debe ser mayor a \$0.00 y no exceder el saldo pendiente.")
            }
            return
        }

        // Construir asignaciones monetarias y de stock de acuerdo a las reglas de negocio
        val allocations = buildAllocations(state, totalPaymentCents)
        if (allocations.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "No se pudieron construir las asignaciones de pago requeridas.")
            }
            return
        }

        val request = PaymentRequest(
            orderId = order.id,
            amountCents = totalPaymentCents,
            method = state.selectedMethod,
            allocations = allocations,
        )

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = paymentRepository.processPayment(request)
            result.onSuccess { command ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        lastPaymentCommand = command,
                        successMessage = "Pago de ${formatPesos(totalPaymentCents)} registrado correctamente.",
                        cashReceivedInput = "",
                    )
                }
                // Refrescar órdenes para actualizar saldos y estado cerrado si liquidó
                orderRepository.refreshOrders()
                onPaymentSuccess?.invoke()
            }.onFailure { err ->
                val errorMsg = when {
                    err.message?.contains("saldo pendiente", ignoreCase = true) == true ->
                        "El pago supera el saldo pendiente de la comanda."
                    err.message?.contains("stock", ignoreCase = true) == true ->
                        "Stock insuficiente en almacén para completar el cobro."
                    err.message?.contains("sesión", ignoreCase = true) == true ->
                        "Sesión no autorizada o expirada."
                    else -> err.localizedMessage ?: "Error al procesar el pago."
                }
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = errorMsg)
                }
            }
        }
    }

    /**
     * Construye las asignaciones asegurando que cada partida reciba su cuota
     * y su consumo de existencias correspondiente sin exceder las unidades ordenadas.
     */
    private fun buildAllocations(state: PaymentUiState, targetAmountCents: Long): List<PaymentAllocation> {
        val pending = state.pendingDetails
        if (pending.isEmpty()) return emptyList()

        return when (state.splitMode) {
            SplitMode.FULL -> {
                pending.map { line ->
                    PaymentAllocation(
                        orderDetailId = line.id,
                        quantity = line.quantity,
                        amountCents = line.quantity.toLong() * line.unitPriceCents,
                        stockQuantity = line.quantity,
                    )
                }
            }
            SplitMode.BY_ITEMS -> {
                pending.mapNotNull { line ->
                    val qtyToPay = state.selectedItemQuantities[line.id] ?: 0
                    if (qtyToPay > 0) {
                        PaymentAllocation(
                            orderDetailId = line.id,
                            quantity = qtyToPay,
                            amountCents = qtyToPay.toLong() * line.unitPriceCents,
                            stockQuantity = qtyToPay,
                        )
                    } else null
                }
            }
            SplitMode.EQUAL_SPLIT -> {
                // Distribuir el importe equitativo a lo largo de las partidas pendientes
                var remainingToAllocate = targetAmountCents
                val result = mutableListOf<PaymentAllocation>()

                for (line in pending) {
                    if (remainingToAllocate <= 0L) break
                    val lineTotal = line.quantity.toLong() * line.unitPriceCents
                    val amountForThisLine = minOf(remainingToAllocate, lineTotal)
                    if (amountForThisLine > 0L) {
                        // Unidades completas cubiertas por esta aportación monetaria
                        val unitsCovered = (amountForThisLine / line.unitPriceCents)
                            .toInt()
                            .coerceIn(0, line.quantity)

                        result.add(
                            PaymentAllocation(
                                orderDetailId = line.id,
                                quantity = line.quantity,
                                amountCents = amountForThisLine,
                                stockQuantity = unitsCovered,
                            )
                        )
                        remainingToAllocate -= amountForThisLine
                    }
                }
                result
            }
        }
    }

    private fun formatPesos(cents: Long): String {
        val pesos = cents / 100.0
        return String.format("$%.2f", pesos)
    }
}
