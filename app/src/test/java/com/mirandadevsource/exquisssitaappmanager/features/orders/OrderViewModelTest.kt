package com.mirandadevsource.exquisssitaappmanager.features.orders

import com.mirandadevsource.exquisssitaappmanager.domain.entity.AddOrderItemRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OpenOrderRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderPageFilter
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderViewModelTest {

    private val sampleTables = listOf(
        Table(id = "tbl-1", number = "Mesa 1", status = TableStatus.OCCUPIED, openOrderCount = 1, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", syncVersion = 1),
        Table(id = "tbl-2", number = "Mesa 2", status = TableStatus.FREE, openOrderCount = 0, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", syncVersion = 1),
    )

    private val sampleProducts = listOf(
        Product(id = "p-1", nombre = "Taco de Pastor", precioCentavos = 2500, categoria = "Tacos", controlaInventario = true, stockActual = 50, stockMinimo = 10, activo = true, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", syncVersion = 1),
        Product(id = "p-2", nombre = "Coca Cola 600ml", precioCentavos = 3000, categoria = "Refrescos", controlaInventario = true, stockActual = 24, stockMinimo = 5, activo = true, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", syncVersion = 1),
        Product(id = "p-3", nombre = "Gordita de Chicharrón", precioCentavos = 3500, categoria = "Gorditas", controlaInventario = false, stockActual = null, stockMinimo = null, activo = true, createdAt = "2026-10-01T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z", syncVersion = 1),
    )

    private val sampleOrders = listOf(
        Order(
            id = "ord-101",
            tableId = "tbl-1",
            customerId = null,
            serviceType = ServiceType.TABLE,
            status = OrderStatus.OPEN,
            openedAt = "2026-10-12T14:00:00Z",
            closedAt = null,
            notes = "Sin cebolla en tacos",
            createdAt = "2026-10-12T14:00:00Z",
            updatedAt = "2026-10-12T14:00:00Z",
            syncVersion = 1,
        ),
        Order(
            id = "ord-102",
            tableId = null,
            customerId = null,
            serviceType = ServiceType.TAKEOUT,
            status = OrderStatus.OPEN,
            openedAt = "2026-10-12T14:15:00Z",
            closedAt = null,
            notes = "Para llevar urgente",
            createdAt = "2026-10-12T14:15:00Z",
            updatedAt = "2026-10-12T14:15:00Z",
            syncVersion = 1,
        ),
        Order(
            id = "ord-103",
            tableId = null,
            customerId = null,
            serviceType = ServiceType.DELIVERY,
            status = OrderStatus.CLOSED,
            openedAt = "2026-10-12T12:00:00Z",
            closedAt = "2026-10-12T12:45:00Z",
            notes = null,
            createdAt = "2026-10-12T12:00:00Z",
            updatedAt = "2026-10-12T12:45:00Z",
            syncVersion = 1,
        ),
        Order(
            id = "ord-104",
            tableId = "tbl-1",
            customerId = null,
            serviceType = ServiceType.TABLE,
            status = OrderStatus.CANCELLED,
            openedAt = "2026-10-12T11:00:00Z",
            closedAt = "2026-10-12T11:10:00Z",
            notes = "Mesa cancelada por cliente",
            createdAt = "2026-10-12T11:00:00Z",
            updatedAt = "2026-10-12T11:10:00Z",
            syncVersion = 1,
        ),
    )

    private val sampleDetails = listOf(
        OrderDetail(
            id = "det-1",
            orderId = "ord-101",
            productId = "p-1",
            quantity = 3,
            unitPriceCents = 2500, // 3 * 2500 = 7500
            paymentStatus = PaymentLineStatus.PENDING,
            notes = "Bien dorados",
            createdAt = "2026-10-12T14:05:00Z",
            updatedAt = "2026-10-12T14:05:00Z",
            syncVersion = 1,
        ),
        OrderDetail(
            id = "det-2",
            orderId = "ord-101",
            productId = "p-2",
            quantity = 1,
            unitPriceCents = 3000, // 1 * 3000 = 3000
            paymentStatus = PaymentLineStatus.PAID,
            notes = null,
            createdAt = "2026-10-12T14:05:00Z",
            updatedAt = "2026-10-12T14:05:00Z",
            syncVersion = 1,
        ),
        OrderDetail(
            id = "det-3",
            orderId = "ord-101",
            productId = "p-3",
            quantity = 1,
            unitPriceCents = 3500,
            paymentStatus = PaymentLineStatus.CANCELLED,
            notes = "Se equivocó de sabor",
            createdAt = "2026-10-12T14:05:00Z",
            updatedAt = "2026-10-12T14:05:00Z",
            syncVersion = 1,
        ),
    )

    @Test
    fun `OrderUiState KPI counts are calculated correctly`() {
        val state = OrderUiState(orders = sampleOrders, tables = sampleTables)

        assertEquals(4, state.totalOrdersCount)
        assertEquals(2, state.openOrdersCount)
        assertEquals(1, state.openTableOrdersCount)
        assertEquals(1, state.openTakeoutOrdersCount)
    }

    @Test
    fun `OrderUiState status filtering works accurately`() {
        val state = OrderUiState(orders = sampleOrders, tables = sampleTables)

        // TODAS
        assertEquals(4, state.filteredOrders.size)

        // ABIERTAS
        val openState = state.copy(statusFilter = OrderFilter.ABIERTAS)
        assertEquals(2, openState.filteredOrders.size)
        assertTrue(openState.filteredOrders.all { it.status == OrderStatus.OPEN })

        // PARA_LLEVAR
        val takeoutState = state.copy(statusFilter = OrderFilter.PARA_LLEVAR)
        assertEquals(2, takeoutState.filteredOrders.size)
        assertTrue(takeoutState.filteredOrders.all { it.serviceType != ServiceType.TABLE })

        // CERRADAS
        val closedState = state.copy(statusFilter = OrderFilter.CERRADAS)
        assertEquals(2, closedState.filteredOrders.size)
        assertTrue(closedState.filteredOrders.all { it.status == OrderStatus.CLOSED || it.status == OrderStatus.CANCELLED })
    }

    @Test
    fun `OrderUiState search query filters by folio, mesa or notes`() {
        val state = OrderUiState(orders = sampleOrders, tables = sampleTables)

        // Buscar por folio
        val folioSearch = state.copy(searchQuery = "102")
        assertEquals(1, folioSearch.filteredOrders.size)
        assertEquals("ord-102", folioSearch.filteredOrders.first().id)

        // Buscar por nota
        val notesSearch = state.copy(searchQuery = "cebolla")
        assertEquals(1, notesSearch.filteredOrders.size)
        assertEquals("ord-101", notesSearch.filteredOrders.first().id)

        // Buscar por nombre de mesa
        val tableSearch = state.copy(searchQuery = "mesa 1")
        assertEquals(2, tableSearch.filteredOrders.size)
    }

    @Test
    fun `OrderUiState scopeTab filters today vs historical orders`() {
        val state = OrderUiState(orders = sampleOrders, tables = sampleTables)

        // Sin scopeTab definido (null) retorna todas
        assertEquals(4, state.filteredOrders.size)

        // Con scopeTab DEL_DIA: incluye órdenes abiertas (ord-101 y ord-102)
        val todayState = state.copy(scopeTab = OrderScopeTab.DEL_DIA)
        assertEquals(2, todayState.filteredOrders.size)
        assertTrue(todayState.filteredOrders.all { it.status == OrderStatus.OPEN })

        // Con scopeTab HISTORIAL: incluye órdenes pasadas cerradas/canceladas (ord-103 y ord-104)
        val historyState = state.copy(scopeTab = OrderScopeTab.HISTORIAL)
        assertEquals(2, historyState.filteredOrders.size)
        assertTrue(historyState.filteredOrders.all { it.status != OrderStatus.OPEN })
    }

    @Test
    fun `OrderUiState calculates monetary totals correctly`() {
        val state = OrderUiState(selectedOrderDetails = sampleDetails)

        // det-1: 3 * 2500 = 7500 (PENDING)
        // det-2: 1 * 3000 = 3000 (PAID)
        // det-3: CANCELLED (ignorado en total no cancelado)
        assertEquals(7500L, state.selectedOrderPendingCents)
        assertEquals(3000L, state.selectedOrderPaidCents)
        assertEquals(10500L, state.selectedOrderTotalCents)
    }

    @Test
    fun `OrderUiState product category and text filtering works`() {
        val state = OrderUiState(products = sampleProducts)

        // Todas las categorías
        assertEquals(3, state.filteredProducts.size)

        // Filtro por categoría Tacos
        val tacosState = state.copy(selectedCategory = "Tacos")
        assertEquals(1, tacosState.filteredProducts.size)
        assertEquals("Taco de Pastor", tacosState.filteredProducts.first().nombre)

        // Búsqueda por texto "coca"
        val cocaSearch = state.copy(productSearchQuery = "coca")
        assertEquals(1, cocaSearch.filteredProducts.size)
        assertEquals("Coca Cola 600ml", cocaSearch.filteredProducts.first().nombre)
    }

    @Test
    fun `FakeOrderRepository openOrder adds order and supports table requirement`() = runBlocking {
        val repo = FakeOrderRepository()

        // Crear orden de mesa
        val tableReq = OpenOrderRequest(tableId = "tbl-1", serviceType = ServiceType.TABLE, notes = "Mesa 1")
        val order1Result = repo.openOrder(tableReq)
        assertTrue(order1Result.isSuccess)

        // Crear orden para llevar sin mesa
        val takeoutReq = OpenOrderRequest(tableId = null, serviceType = ServiceType.TAKEOUT, notes = "Para llevar")
        val order2Result = repo.openOrder(takeoutReq)
        assertTrue(order2Result.isSuccess)

        val orders = repo.observeOrders().first()
        assertEquals(2, orders.size)
    }

    @Test
    fun `FakeOrderRepository addItem and updateItemQuantity operate reactively`() = runBlocking {
        val repo = FakeOrderRepository()
        val openResult = repo.openOrder(OpenOrderRequest(tableId = "tbl-1", serviceType = ServiceType.TABLE))
        val orderId = openResult.getOrThrow()

        // Agregar producto
        val addResult = repo.addItem(
            AddOrderItemRequest(
                orderId = orderId,
                productId = "p-1",
                quantity = 2,
                unitPriceCents = 2500,
                notes = "Con todo",
            )
        )
        assertTrue(addResult.isSuccess)
        val itemId = addResult.getOrThrow()

        var items = repo.observeOrderDetails(orderId).first()
        assertEquals(1, items.size)
        assertEquals(2, items.first().quantity)
        assertEquals(5000L, items.first().lineTotalCents)

        // Actualizar cantidad a 5
        val updateResult = repo.updateItemQuantity(itemId, 5)
        assertTrue(updateResult.isSuccess)

        items = repo.observeOrderDetails(orderId).first()
        assertEquals(5, items.first().quantity)
        assertEquals(12500L, items.first().lineTotalCents)

        // Cancelar partida
        val cancelResult = repo.cancelItem(itemId)
        assertTrue(cancelResult.isSuccess)

        items = repo.observeOrderDetails(orderId).first()
        assertEquals(PaymentLineStatus.CANCELLED, items.first().paymentStatus)
    }

    @Test
    fun `FakeOrderRepository cancelOrder marks order as cancelled`() = runBlocking {
        val repo = FakeOrderRepository()
        val orderId = repo.openOrder(OpenOrderRequest(tableId = null, serviceType = ServiceType.TAKEOUT)).getOrThrow()

        val cancelResult = repo.cancelOrder(orderId)
        assertTrue(cancelResult.isSuccess)

        val order = repo.observeOrder(orderId).first()
        assertNotNull(order)
        assertEquals(OrderStatus.CANCELLED, order?.status)
    }

    // --- Fakes para pruebas ---

    private class FakeOrderRepository : OrderRepository {
        private val ordersFlow = MutableStateFlow<List<Order>>(emptyList())
        private val detailsFlow = MutableStateFlow<Map<String, List<OrderDetail>>>(emptyMap())

        override fun observeOrders(): Flow<List<Order>> = ordersFlow

        override fun observeOrdersForTable(tableId: String): Flow<List<Order>> =
            flowOf(ordersFlow.value.filter { it.tableId == tableId })

        override fun observeOrder(orderId: String): Flow<Order?> =
            flowOf(ordersFlow.value.firstOrNull { it.id == orderId })

        override fun observeOrderDetails(orderId: String): Flow<List<OrderDetail>> =
            flowOf(detailsFlow.value[orderId] ?: emptyList())

        override suspend fun refreshOrders(): Result<Unit> = Result.success(Unit)

        override suspend fun getOrderPage(filter: OrderPageFilter, page: PageRequest): Result<DataPage<Order>> =
            Result.success(com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList(ordersFlow.value, page, filter.scope()))

        override suspend fun openOrder(request: OpenOrderRequest): Result<String> {
            val id = "ord-${System.currentTimeMillis()}"
            val newOrder = Order(
                id = id,
                tableId = request.tableId,
                customerId = request.customerId,
                serviceType = request.serviceType,
                status = OrderStatus.OPEN,
                openedAt = "2026-10-12T14:00:00Z",
                closedAt = null,
                notes = request.notes,
                createdAt = "2026-10-12T14:00:00Z",
                updatedAt = "2026-10-12T14:00:00Z",
                syncVersion = 1,
            )
            ordersFlow.value = ordersFlow.value + newOrder
            return Result.success(id)
        }

        override suspend fun addItem(request: AddOrderItemRequest): Result<String> {
            val id = "det-${System.currentTimeMillis()}"
            val newDetail = OrderDetail(
                id = id,
                orderId = request.orderId,
                productId = request.productId,
                quantity = request.quantity,
                unitPriceCents = request.unitPriceCents,
                paymentStatus = PaymentLineStatus.PENDING,
                notes = request.notes,
                createdAt = "2026-10-12T14:01:00Z",
                updatedAt = "2026-10-12T14:01:00Z",
                syncVersion = 1,
            )
            val currentMap = detailsFlow.value.toMutableMap()
            val list = currentMap[request.orderId]?.toMutableList() ?: mutableListOf()
            list.add(newDetail)
            currentMap[request.orderId] = list
            detailsFlow.value = currentMap
            return Result.success(id)
        }

        override suspend fun updateItemQuantity(detailId: String, quantity: Int): Result<String> {
            val currentMap = detailsFlow.value.toMutableMap()
            for ((ordId, list) in currentMap) {
                val index = list.indexOfFirst { it.id == detailId }
                if (index != -1) {
                    val updated = list.toMutableList()
                    updated[index] = updated[index].copy(quantity = quantity)
                    currentMap[ordId] = updated
                    detailsFlow.value = currentMap
                    return Result.success(detailId)
                }
            }
            return Result.failure(NoSuchElementException())
        }

        override suspend fun cancelItem(detailId: String): Result<String> {
            val currentMap = detailsFlow.value.toMutableMap()
            for ((ordId, list) in currentMap) {
                val index = list.indexOfFirst { it.id == detailId }
                if (index != -1) {
                    val updated = list.toMutableList()
                    updated[index] = updated[index].copy(paymentStatus = PaymentLineStatus.CANCELLED)
                    currentMap[ordId] = updated
                    detailsFlow.value = currentMap
                    return Result.success(detailId)
                }
            }
            return Result.failure(NoSuchElementException())
        }

        override suspend fun cancelOrder(orderId: String): Result<String> {
            ordersFlow.value = ordersFlow.value.map {
                if (it.id == orderId) it.copy(status = OrderStatus.CANCELLED) else it
            }
            return Result.success(orderId)
        }

        override suspend fun syncPendingCommands(): Result<Unit> = Result.success(Unit)
    }
}
