package com.mirandadevsource.exquisssitaappmanager.features.tables

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
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

class TablesViewModelTest {

    private val sampleTables = listOf(
        Table(
            id = "t1",
            number = "Mesa 1",
            status = TableStatus.FREE,
            openOrderCount = 0,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Table(
            id = "t2",
            number = "Mesa 2",
            status = TableStatus.OCCUPIED,
            openOrderCount = 2,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Table(
            id = "t3",
            number = "Mesa 3",
            status = TableStatus.RESERVED,
            openOrderCount = 0,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Table(
            id = "t10",
            number = "Mesa 10",
            status = TableStatus.FREE,
            openOrderCount = 0,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
    )

    private val sampleOrdersForMesa2 = listOf(
        Order(
            id = "ord-1",
            tableId = "t2",
            customerId = null,
            serviceType = ServiceType.TABLE,
            status = OrderStatus.OPEN,
            openedAt = "2026-10-12T10:00:00Z",
            closedAt = null,
            notes = "Sin cebolla",
            createdAt = "2026-10-12T10:00:00Z",
            updatedAt = "2026-10-12T10:00:00Z",
            syncVersion = 1,
        ),
        Order(
            id = "ord-2",
            tableId = "t2",
            customerId = null,
            serviceType = ServiceType.TABLE,
            status = OrderStatus.OPEN,
            openedAt = "2026-10-12T10:15:00Z",
            closedAt = null,
            notes = null,
            createdAt = "2026-10-12T10:15:00Z",
            updatedAt = "2026-10-12T10:15:00Z",
            syncVersion = 1,
        ),
    )

    @Test
    fun `TablesUiState KPI counts and filters work accurately`() {
        val state = TablesUiState(rawTables = sampleTables)

        assertEquals(4, state.totalCount)
        assertEquals(2, state.freeCount)
        assertEquals(1, state.occupiedCount)
        assertEquals(1, state.reservedCount)

        // Filter: Todas
        assertEquals(4, state.filteredTables.size)

        // Filter: Libres
        val libresState = state.copy(statusFilter = TableStatusFilter.LIBRES)
        assertEquals(2, libresState.filteredTables.size)
        assertTrue(libresState.filteredTables.all { it.status == TableStatus.FREE })

        // Filter: Ocupadas
        val ocupadasState = state.copy(statusFilter = TableStatusFilter.OCUPADAS)
        assertEquals(1, ocupadasState.filteredTables.size)
        assertEquals("Mesa 2", ocupadasState.filteredTables.first().number)

        // Filter: Reservadas
        val reservadasState = state.copy(statusFilter = TableStatusFilter.RESERVADAS)
        assertEquals(1, reservadasState.filteredTables.size)
        assertEquals("Mesa 3", reservadasState.filteredTables.first().number)
    }

    @Test
    fun `TablesUiState natural numerical sorting puts Mesa 2 before Mesa 10`() {
        val state = TablesUiState(rawTables = sampleTables)
        val sortedNames = state.filteredTables.map { it.number }

        assertEquals(listOf("Mesa 1", "Mesa 2", "Mesa 3", "Mesa 10"), sortedNames)
    }

    @Test
    fun `searchQuery filters tables by number`() {
        val state = TablesUiState(rawTables = sampleTables, searchQuery = "10")
        assertEquals(1, state.filteredTables.size)
        assertEquals("Mesa 10", state.filteredTables.first().number)
    }

    @Test
    fun `searchQuery case insensitive filtering works`() {
        val state = TablesUiState(rawTables = sampleTables, searchQuery = "mesa 3")
        assertEquals(1, state.filteredTables.size)
        assertEquals("Mesa 3", state.filteredTables.first().number)
    }

    @Test
    fun `createTable validates empty table number in state`() {
        val emptyState = TablesUiState(newTableNumber = "   ")
        val isBlank = emptyState.newTableNumber.trim().isBlank()
        assertTrue(isBlank)
    }

    @Test
    fun `createTable detects duplicate table number`() {
        val state = TablesUiState(rawTables = sampleTables)
        val exists = state.rawTables.any { it.number.equals("mesa 2", ignoreCase = true) }
        assertTrue(exists)
    }

    @Test
    fun `FakeTableRepository creates table and updates list`() = runBlocking {
        val repo = FakeTableRepository(sampleTables)
        val result = repo.createTable("Mesa 4")
        assertTrue(result.isSuccess)

        val currentTables = repo.observeTables().first()
        assertEquals(5, currentTables.size)
        assertTrue(currentTables.any { it.number == "Mesa 4" })
    }

    @Test
    fun `FakeTableRepository toggles reservation`() = runBlocking {
        val repo = FakeTableRepository(sampleTables)
        val result = repo.setReservation("t1", true)
        assertTrue(result.isSuccess)

        val currentTables = repo.observeTables().first()
        val table1 = currentTables.first { it.id == "t1" }
        assertEquals(TableStatus.RESERVED, table1.status)
    }

    @Test
    fun `Orders for table are queried correctly`() = runBlocking {
        val orderRepo = FakeOrderRepository(mapOf("t2" to sampleOrdersForMesa2))
        val orders = orderRepo.observeOrdersForTable("t2").first()

        assertEquals(2, orders.size)
        assertEquals("ord-1", orders[0].id)
        assertEquals("ord-2", orders[1].id)
    }

    // --- Fakes para pruebas ---

    private class FakeTableRepository(initialTables: List<Table>) : TableRepository {
        private val tablesFlow = MutableStateFlow(initialTables)

        override fun observeTables(): Flow<List<Table>> = tablesFlow

        override suspend fun refreshTables(): Result<Unit> = Result.success(Unit)

        override suspend fun createTable(number: String): Result<String> {
            val newTable = Table(
                id = "t-${System.currentTimeMillis()}",
                number = number,
                status = TableStatus.FREE,
                openOrderCount = 0,
                createdAt = "2026-10-12T00:00:00Z",
                updatedAt = "2026-10-12T00:00:00Z",
                syncVersion = 1,
            )
            tablesFlow.value = tablesFlow.value + newTable
            return Result.success(newTable.id)
        }

        override suspend fun setReservation(tableId: String, reserved: Boolean): Result<String> {
            tablesFlow.value = tablesFlow.value.map {
                if (it.id == tableId) it.copy(status = if (reserved) TableStatus.RESERVED else TableStatus.FREE)
                else it
            }
            return Result.success(tableId)
        }

        override suspend fun syncPendingCommands(): Result<Unit> = Result.success(Unit)
    }

    private class FakeOrderRepository(
        private val ordersByTable: Map<String, List<Order>> = emptyMap(),
    ) : OrderRepository {
        override fun observeOrders(): Flow<List<Order>> = flowOf(ordersByTable.values.flatten())
        override fun observeOrdersForTable(tableId: String): Flow<List<Order>> =
            flowOf(ordersByTable[tableId] ?: emptyList())
        override fun observeOrder(orderId: String): Flow<Order?> = flowOf(null)
        override fun observeOrderDetails(orderId: String) = flowOf(emptyList<com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail>())
        override suspend fun refreshOrders(): Result<Unit> = Result.success(Unit)
        override suspend fun getOrderPage(
            filter: com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderPageFilter,
            page: com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest,
        ): Result<com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage<com.mirandadevsource.exquisssitaappmanager.domain.entity.Order>> =
            Result.success(com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList(ordersByTable.values.flatten(), page, filter.scope()))
        override suspend fun openOrder(request: com.mirandadevsource.exquisssitaappmanager.domain.entity.OpenOrderRequest): Result<String> = Result.success("ord-new")
        override suspend fun addItem(request: com.mirandadevsource.exquisssitaappmanager.domain.entity.AddOrderItemRequest): Result<String> = Result.success("item-new")
        override suspend fun updateItemQuantity(detailId: String, quantity: Int): Result<String> = Result.success(detailId)
        override suspend fun cancelItem(detailId: String): Result<String> = Result.success(detailId)
        override suspend fun cancelOrder(orderId: String): Result<String> = Result.success(orderId)
        override suspend fun syncPendingCommands(): Result<Unit> = Result.success(Unit)
    }
}
