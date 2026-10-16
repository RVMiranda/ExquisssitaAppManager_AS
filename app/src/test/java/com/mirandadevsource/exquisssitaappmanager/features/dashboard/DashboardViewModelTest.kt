package com.mirandadevsource.exquisssitaappmanager.features.dashboard

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TransactionEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardViewModelTest {

    private val mexicoZone = ZoneId.of("America/Mexico_City")
    private val todayStr = LocalDate.now(mexicoZone).toString()
    private val todayWeekday = LocalDate.now(mexicoZone).dayOfWeek.value

    @Test
    fun `DashboardUiState computes table availability accurately`() {
        val tables = listOf(
            Table("1", "Mesa 1", TableStatus.FREE, 0, "2026-10-01", "2026-10-01", 1),
            Table("2", "Mesa 2", TableStatus.OCCUPIED, 1, "2026-10-01", "2026-10-01", 1),
            Table("3", "Mesa 3", TableStatus.FREE, 0, "2026-10-01", "2026-10-01", 1),
            Table("4", "Mesa 4", TableStatus.RESERVED, 0, "2026-10-01", "2026-10-01", 1),
        )

        val free = tables.count { it.status == TableStatus.FREE }
        val total = tables.size
        val state = DashboardUiState(freeTables = free, totalTables = total)

        assertEquals(2, state.freeTables)
        assertEquals(4, state.totalTables)
        assertEquals("2 / 4", state.tableAvailabilityLabel)
    }

    @Test
    fun `DashboardUiState handles zero total tables gracefully`() {
        val state = DashboardUiState(freeTables = 0, totalTables = 0)
        assertEquals("0", state.tableAvailabilityLabel)
    }

    @Test
    fun `today orders and live open orders are correctly categorized`() {
        val orders = listOf(
            Order("o1", "1", null, ServiceType.TABLE, OrderStatus.OPEN, "${todayStr}T10:00:00Z", null, null, "", "", 1),
            Order("o2", "2", null, ServiceType.TABLE, OrderStatus.CLOSED, "${todayStr}T11:00:00Z", "${todayStr}T12:00:00Z", null, "", "", 1),
            Order("o3", null, null, ServiceType.TAKEOUT, OrderStatus.OPEN, "2026-10-01T10:00:00Z", null, null, "", "", 1),
        )

        val todayOrders = orders.filter { it.openedAt.startsWith(todayStr) }
        val openOrders = orders.count { it.status == OrderStatus.OPEN }

        val state = DashboardUiState(
            todayOrdersCount = todayOrders.size,
            openOrdersCount = openOrders,
        )

        assertEquals(2, state.todayOrdersCount)
        assertEquals(2, state.openOrdersCount)
    }

    @Test
    fun `sales of today are summed exclusively from today transactions`() {
        val txs = listOf(
            TransactionEntity("tx1", "o1", 15000L, "Efectivo", "${todayStr}T11:00:00", "k1", "", 1),
            TransactionEntity("tx2", "o2", 8500L, "Tarjeta", "${todayStr}T12:30:00", "k2", "", 1),
            TransactionEntity("tx3", "o3", 5000L, "Efectivo", "2026-10-01T12:00:00", "k3", "", 1),
        )

        val todayTxs = txs.filter { it.fecha.startsWith(todayStr) }
        val salesCents = todayTxs.sumOf { it.montoCentavos }

        val state = DashboardUiState(
            todaySalesCents = salesCents,
            todayPaymentsCount = todayTxs.size,
        )

        assertEquals(23500L, state.todaySalesCents)
        assertEquals(2, state.todayPaymentsCount)
    }

    @Test
    fun `low stock filtering detects only inventory controlled products at or below minimum`() {
        val products = listOf(
            Product("p1", "Coca Cola 600ml", 3500L, "Refrescos", controlaInventario = true, stockActual = 2, stockMinimo = 5, activo = true, createdAt = "", updatedAt = "", syncVersion = 1),
            Product("p2", "Agua Melón", 3500L, "Aguas N", controlaInventario = true, stockActual = 10, stockMinimo = 5, activo = true, createdAt = "", updatedAt = "", syncVersion = 1),
            Product("p3", "Tacos de Pastor", 2250L, "Tacos", controlaInventario = false, stockActual = 0, stockMinimo = 0, activo = true, createdAt = "", updatedAt = "", syncVersion = 1),
        )

        val lowStockItems = products.filter {
            it.controlaInventario && (it.stockActual ?: 0) <= (it.stockMinimo ?: 0)
        }.map {
            LowStockProductItem(it.id, it.nombre, it.stockActual ?: 0, it.stockMinimo ?: 0, it.categoria)
        }

        val state = DashboardUiState(lowStockProducts = lowStockItems)

        assertTrue(state.hasLowStockAlert)
        assertEquals(1, state.lowStockProducts.size)
        assertEquals("Coca Cola 600ml", state.lowStockProducts.first().name)
        assertEquals(2, state.lowStockProducts.first().currentStock)
        assertEquals(5, state.lowStockProducts.first().minStock)
    }

    @Test
    fun `payroll alert detects employees scheduled for today without confirmed payment`() {
        val empToday = Employee("e1", null, "Carlos", "Ruiz", "5551234567", 150000L, diaPago = todayWeekday, activo = true, createdAt = "", updatedAt = "")
        val empOtherDay = Employee("e2", null, "Ana", "López", "5559876543", 120000L, diaPago = if (todayWeekday == 1) 2 else 1, activo = true, createdAt = "", updatedAt = "")

        val employees = listOf(empToday, empOtherDay)
        val pendingToday = employees.filter { it.diaPago == todayWeekday }.map {
            EmployeePayItem(it.id, it.fullName, it.salarioCents, it.diaPagoNombre)
        }

        val state = DashboardUiState(employeesToPayToday = pendingToday)

        assertTrue(state.hasPayrollAlert)
        assertEquals(1, state.employeesToPayToday.size)
        assertEquals("Carlos Ruiz", state.employeesToPayToday.first().name)
        assertEquals(150000L, state.employeesToPayToday.first().wageCents)
    }

    @Test
    fun `pending synchronization alert triggers when order or inventory outbox has pending items`() {
        val orderPending = 2
        val invPending = 1
        val state = DashboardUiState(pendingSyncCount = orderPending + invPending)

        assertTrue(state.hasPendingSyncAlert)
        assertEquals(3, state.pendingSyncCount)

        val syncedState = DashboardUiState(pendingSyncCount = 0)
        assertFalse(syncedState.hasPendingSyncAlert)
    }
}
