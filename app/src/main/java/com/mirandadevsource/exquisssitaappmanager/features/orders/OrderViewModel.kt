package com.mirandadevsource.exquisssitaappmanager.features.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AddOrderItemRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OpenOrderRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
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
class OrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val tableRepository: TableRepository,
    private val inventoryRepository: InventoryRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState(isLoading = true))
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private var detailsJob: Job? = null
    private var paymentsJob: Job? = null

    init {
        // Observar órdenes reactivamente
        viewModelScope.launch {
            orderRepository.observeOrders().collect { list ->
                _uiState.update { it.copy(orders = list, isLoading = false) }
            }
        }

        // Observar mesas reactivamente para nombres y estados
        viewModelScope.launch {
            tableRepository.observeTables().collect { list ->
                _uiState.update { it.copy(tables = list) }
            }
        }

        // Cargar catálogo de productos y sincronizar
        loadProducts()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            orderRepository.refreshOrders()
            tableRepository.refreshTables()
            inventoryRepository.getProducts(includeInactive = false)
                .onSuccess { productList ->
                    _uiState.update { it.copy(products = productList) }
                }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            inventoryRepository.getProducts(includeInactive = false)
                .onSuccess { productList ->
                    _uiState.update { it.copy(products = productList) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChange(filter: OrderFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun filterByTable(tableId: String?) {
        _uiState.update { it.copy(tableFilterId = tableId) }
    }

    // --- Modal Apertura de Comanda ---
    fun openNewOrderDialog(
        initialTableId: String? = null,
        initialServiceType: ServiceType = if (initialTableId != null) ServiceType.TABLE else ServiceType.TABLE,
    ) {
        _uiState.update {
            it.copy(
                isOpenOrderDialogOpen = true,
                newOrderServiceType = initialServiceType,
                newOrderTableId = initialTableId,
                newOrderNotes = "",
                openOrderError = null,
                isSubmittingOrder = false,
            )
        }
    }

    fun dismissNewOrderDialog() {
        _uiState.update {
            it.copy(
                isOpenOrderDialogOpen = false,
                openOrderError = null,
                isSubmittingOrder = false,
            )
        }
    }

    fun onNewOrderServiceTypeChange(type: ServiceType) {
        _uiState.update {
            it.copy(
                newOrderServiceType = type,
                newOrderTableId = if (type == ServiceType.TABLE) it.newOrderTableId else null,
                openOrderError = null,
            )
        }
    }

    fun onNewOrderTableSelect(tableId: String?) {
        _uiState.update { it.copy(newOrderTableId = tableId, openOrderError = null) }
    }

    fun onNewOrderNotesChange(notes: String) {
        _uiState.update { it.copy(newOrderNotes = notes) }
    }

    fun createOrder() {
        val currentState = _uiState.value
        if (currentState.newOrderServiceType == ServiceType.TABLE && currentState.newOrderTableId.isNullOrBlank()) {
            _uiState.update { it.copy(openOrderError = "Selecciona una mesa para servicio en comedor.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOrder = true, openOrderError = null) }
            val request = OpenOrderRequest(
                tableId = if (currentState.newOrderServiceType == ServiceType.TABLE) currentState.newOrderTableId else null,
                serviceType = currentState.newOrderServiceType,
                notes = currentState.newOrderNotes.trim().ifBlank { null },
            )
            val result = orderRepository.openOrder(request)
            result.onSuccess { orderId ->
                _uiState.update {
                    it.copy(
                        isOpenOrderDialogOpen = false,
                        isSubmittingOrder = false,
                        successMessage = "Comanda abierta exitosamente.",
                    )
                }
                selectOrder(orderId)
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isSubmittingOrder = false,
                        openOrderError = ex.localizedMessage ?: "No se pudo abrir la comanda.",
                    )
                }
            }
        }
    }

    // --- Detalle de Comanda Seleccionada ---
    fun selectOrder(orderId: String) {
        detailsJob?.cancel()
        paymentsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedOrderId = orderId,
                isLoadingOrderDetails = true,
                orderActionError = null,
            )
        }
        detailsJob = viewModelScope.launch {
            orderRepository.observeOrderDetails(orderId).collect { details ->
                _uiState.update {
                    it.copy(
                        selectedOrderDetails = details,
                        isLoadingOrderDetails = false,
                    )
                }
            }
        }
        paymentsJob = viewModelScope.launch {
            paymentRepository.observePayments(orderId).collect { payments ->
                _uiState.update {
                    it.copy(selectedOrderPayments = payments)
                }
            }
        }
    }

    fun dismissOrderDetail() {
        detailsJob?.cancel()
        paymentsJob?.cancel()
        detailsJob = null
        paymentsJob = null
        _uiState.update {
            it.copy(
                selectedOrderId = null,
                selectedOrderDetails = emptyList(),
                selectedOrderPayments = emptyList(),
                isLoadingOrderDetails = false,
                orderActionError = null,
                activeCheckoutOrder = null,
            )
        }
    }

    fun startCheckout(order: Order) {
        _uiState.update { it.copy(activeCheckoutOrder = order) }
    }

    fun dismissCheckout() {
        _uiState.update { it.copy(activeCheckoutOrder = null) }
    }

    // --- Modal Agregar Producto ---
    fun openAddItemDialog() {
        _uiState.update {
            it.copy(
                isAddItemDialogOpen = true,
                productSearchQuery = "",
                selectedCategory = null,
                selectedProduct = null,
                itemQuantity = 1,
                itemNotes = "",
                addItemError = null,
                isSubmittingItem = false,
            )
        }
    }

    fun dismissAddItemDialog() {
        _uiState.update {
            it.copy(
                isAddItemDialogOpen = false,
                addItemError = null,
                isSubmittingItem = false,
            )
        }
    }

    fun onProductSearchQueryChange(query: String) {
        _uiState.update { it.copy(productSearchQuery = query) }
    }

    fun onCategorySelect(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onProductSelect(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                itemQuantity = 1,
                addItemError = null,
            )
        }
    }

    fun onQuantityChange(quantity: Int) {
        if (quantity >= 1) {
            _uiState.update { it.copy(itemQuantity = quantity) }
        }
    }

    fun onItemNotesChange(notes: String) {
        _uiState.update { it.copy(itemNotes = notes) }
    }

    fun addItemToOrder() {
        val currentState = _uiState.value
        val orderId = currentState.selectedOrderId
        val product = currentState.selectedProduct
        if (orderId == null || product == null) {
            _uiState.update { it.copy(addItemError = "Selecciona un producto del catálogo.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingItem = true, addItemError = null) }
            val request = AddOrderItemRequest(
                orderId = orderId,
                productId = product.id,
                quantity = currentState.itemQuantity,
                unitPriceCents = product.precioCentavos,
                notes = currentState.itemNotes.trim().ifBlank { null },
            )
            val result = orderRepository.addItem(request)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isAddItemDialogOpen = false,
                        isSubmittingItem = false,
                        successMessage = "${product.nombre} agregado a la comanda.",
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isSubmittingItem = false,
                        addItemError = ex.localizedMessage ?: "Error al agregar el producto.",
                    )
                }
            }
        }
    }

    // --- Modificación de Cantidad y Cancelación de Ítems ---
    fun updateItemQuantity(detail: OrderDetail, newQuantity: Int) {
        if (newQuantity <= 0) {
            promptCancelItem(detail)
            return
        }

        viewModelScope.launch {
            val result = orderRepository.updateItemQuantity(detail.id, newQuantity)
            result.onFailure { ex ->
                _uiState.update {
                    it.copy(orderActionError = ex.localizedMessage ?: "No se pudo actualizar la cantidad.")
                }
            }
        }
    }

    fun promptCancelItem(item: OrderDetail) {
        _uiState.update { it.copy(itemToCancel = item) }
    }

    fun confirmCancelItem() {
        val item = _uiState.value.itemToCancel ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancellingAction = true) }
            val result = orderRepository.cancelItem(item.id)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        itemToCancel = null,
                        isCancellingAction = false,
                        successMessage = "Partida cancelada de la comanda.",
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        itemToCancel = null,
                        isCancellingAction = false,
                        orderActionError = ex.localizedMessage ?: "No se pudo cancelar la partida.",
                    )
                }
            }
        }
    }

    // --- Cancelación de Comanda Completa ---
    fun promptCancelOrder(order: Order) {
        _uiState.update { it.copy(orderToCancel = order) }
    }

    fun confirmCancelOrder() {
        val order = _uiState.value.orderToCancel ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancellingAction = true) }
            val result = orderRepository.cancelOrder(order.id)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        orderToCancel = null,
                        isCancellingAction = false,
                        selectedOrderId = null,
                        selectedOrderDetails = emptyList(),
                        successMessage = "Comanda cancelada exitosamente.",
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        orderToCancel = null,
                        isCancellingAction = false,
                        orderActionError = ex.localizedMessage ?: "No se pudo cancelar la comanda.",
                    )
                }
            }
        }
    }

    fun dismissCancelDialog() {
        _uiState.update {
            it.copy(
                itemToCancel = null,
                orderToCancel = null,
                isCancellingAction = false,
            )
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, orderActionError = null) }
    }
}
