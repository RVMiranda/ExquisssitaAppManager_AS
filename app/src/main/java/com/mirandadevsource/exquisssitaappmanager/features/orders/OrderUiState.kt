package com.mirandadevsource.exquisssitaappmanager.features.orders

import com.mirandadevsource.exquisssitaappmanager.core.utils.DateUtils
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table

enum class OrderScopeTab(val label: String) {
    DEL_DIA("Comandas de Hoy"),
    HISTORIAL("Historial"),
}

enum class OrderFilter(val label: String) {
    TODAS("Todas"),
    ABIERTAS("Abiertas"),
    PARA_LLEVAR("Para llevar / Domicilio"),
    CERRADAS("Cerradas / Canceladas"),
}

data class OrderUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Ámbito de visualización (Hoy vs Historial)
    val scopeTab: OrderScopeTab? = null,

    // Colecciones núcleo
    val orders: List<Order> = emptyList(),
    val tables: List<Table> = emptyList(),
    val products: List<Product> = emptyList(),

    // Búsqueda y filtrado
    val searchQuery: String = "",
    val statusFilter: OrderFilter = OrderFilter.TODAS,
    val tableFilterId: String? = null,

    // Modal: Apertura de comanda
    val isOpenOrderDialogOpen: Boolean = false,
    val newOrderServiceType: ServiceType = ServiceType.TABLE,
    val newOrderTableId: String? = null,
    val newOrderNotes: String = "",
    val isSubmittingOrder: Boolean = false,
    val openOrderError: String? = null,

    // Detalle / Inspección de comanda seleccionada
    val selectedOrderId: String? = null,
    val selectedOrderDetails: List<OrderDetail> = emptyList(),
    val selectedOrderPayments: List<Payment> = emptyList(),
    val isLoadingOrderDetails: Boolean = false,
    val orderActionError: String? = null,

    // Flujo de Cobro / Checkout activo
    val activeCheckoutOrder: Order? = null,

    // Modal: Agregar ítem al pedido
    val isAddItemDialogOpen: Boolean = false,
    val productSearchQuery: String = "",
    val selectedCategory: String? = null,
    val selectedProduct: Product? = null,
    val itemQuantity: Int = 1,
    val itemCustomPricePesos: String = "",
    val itemNotes: String = "",
    val isSubmittingItem: Boolean = false,
    val addItemError: String? = null,

    // Diálogos de confirmación de cancelación
    val itemToCancel: OrderDetail? = null,
    val orderToCancel: Order? = null,
    val isCancellingAction: Boolean = false,
) {
    val tableMap: Map<String, Table> get() = tables.associateBy { it.id }
    val productMap: Map<String, Product> get() = products.associateBy { it.id }

    val selectedOrder: Order? get() = orders.firstOrNull { it.id == selectedOrderId }

    // Subconjuntos por ámbito temporal
    val todayOrders: List<Order> get() = orders.filter {
        it.status == OrderStatus.OPEN || DateUtils.isTodayInMexico(it.openedAt)
    }

    val historyOrders: List<Order> get() = orders.filter {
        it.status != OrderStatus.OPEN && !DateUtils.isTodayInMexico(it.openedAt)
    }

    // Métricas KPI
    val totalOrdersCount: Int get() = orders.size
    val todayOrdersCount: Int get() = todayOrders.size
    val historyOrdersCount: Int get() = historyOrders.size
    val openOrdersCount: Int get() = orders.count { it.status == OrderStatus.OPEN }
    val openTableOrdersCount: Int get() = orders.count { it.status == OrderStatus.OPEN && it.serviceType == ServiceType.TABLE }
    val openTakeoutOrdersCount: Int get() = orders.count { it.status == OrderStatus.OPEN && it.serviceType != ServiceType.TABLE }

    // Órdenes filtradas
    val filteredOrders: List<Order> get() {
        val mapOfTables = tableMap
        val baseList = when (scopeTab) {
            OrderScopeTab.DEL_DIA -> todayOrders
            OrderScopeTab.HISTORIAL -> historyOrders
            null -> orders
        }
        return baseList.filter { order ->
            // Filtro por mesa específica si está asignado
            if (tableFilterId != null && order.tableId != tableFilterId) return@filter false

            // Filtro por estado
            val matchesFilter = when (statusFilter) {
                OrderFilter.TODAS -> true
                OrderFilter.ABIERTAS -> order.status == OrderStatus.OPEN
                OrderFilter.PARA_LLEVAR -> order.serviceType != ServiceType.TABLE
                OrderFilter.CERRADAS -> order.status == OrderStatus.CLOSED || order.status == OrderStatus.CANCELLED
            }
            if (!matchesFilter) return@filter false

            // Filtro por búsqueda de texto
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                val tableName = order.tableId?.let { id -> mapOfTables[id]?.number }?.lowercase() ?: ""
                val orderId = order.id.lowercase()
                val notes = order.notes?.lowercase() ?: ""
                val serviceType = order.serviceType.databaseValue.lowercase()
                orderId.contains(query) || tableName.contains(query) || notes.contains(query) || serviceType.contains(query)
            } else {
                true
            }
        }
    }

    // Categorías del catálogo
    val availableCategories: List<String> get() {
        val standard = listOf("Refrescos", "Jugos", "Aguas N", "Tacos", "Gorditas", "Carne", "Verdura", "Otros")
        val fromProducts = products.map { it.categoria.trim() }.filter { it.isNotBlank() }
        val combined = linkedSetOf<String>()
        standard.forEach { combined.add(it) }
        fromProducts.forEach { combined.add(it) }
        return combined.toList()
    }

    // Productos filtrados para el selector
    val filteredProducts: List<Product> get() {
        return products.filter { product ->
            val matchesCategory = selectedCategory == null || product.categoria.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = if (productSearchQuery.isNotBlank()) {
                val query = productSearchQuery.trim().lowercase()
                product.nombre.lowercase().contains(query) || product.categoria.lowercase().contains(query)
            } else {
                true
            }
            matchesCategory && matchesQuery
        }
    }

    // Totales calculados para la orden seleccionada
    val selectedOrderPendingCents: Long get() = selectedOrderDetails
        .filter { it.paymentStatus == PaymentLineStatus.PENDING }
        .sumOf { it.lineTotalCents }

    val selectedOrderPaidCents: Long get() = selectedOrderDetails
        .filter { it.paymentStatus == PaymentLineStatus.PAID }
        .sumOf { it.lineTotalCents }

    val selectedOrderTotalCents: Long get() = selectedOrderDetails
        .filter { it.paymentStatus != PaymentLineStatus.CANCELLED }
        .sumOf { it.lineTotalCents }
}
