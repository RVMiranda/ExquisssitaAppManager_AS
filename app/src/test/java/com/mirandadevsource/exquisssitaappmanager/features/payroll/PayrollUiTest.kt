package com.mirandadevsource.exquisssitaappmanager.features.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentSyncStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class PayrollUiTest {

    private val sampleEmployee = Employee(
        id = "emp-1",
        nombre = "Juan",
        apellido = "Pérez",
        telefono = "9611234567",
        salarioCents = 150000L,
        diaPago = 6,
        activo = true,
        userId = null,
        createdAt = "2026-10-01T00:00:00Z",
        updatedAt = "2026-10-01T00:00:00Z",
    )

    private val samplePayment = EmployeePayment(
        id = "pay-1",
        empleadoId = "emp-1",
        montoCents = 150000L,
        fechaPago = "2026-10-17",
        periodoInicio = "2026-10-12",
        fechaProgramada = "2026-10-17",
        notas = "Pago puntual",
        registradoPor = "usr-manager",
        createdAt = "2026-10-17T12:00:00Z",
        syncVersion = 1L,
        syncStatus = PaymentSyncStatus.CONFIRMED,
    )

    private val sampleItem = WeeklyPayrollItem(
        employee = sampleEmployee,
        payment = samplePayment,
    )

    @Test
    fun `initial state has correct default values`() {
        val state = PayrollUiState()
        assertFalse(state.isLoading)
        assertNull(state.summary)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
        assertNull(state.paymentModalItem)
        assertNull(state.viewingHistoryEmployee)
        assertTrue(state.historyPayments.isEmpty())
        assertFalse(state.isLoadingHistory)
        assertFalse(state.isLoadingHistoryPage)
        assertFalse(state.historyHasMore)
        assertNull(state.historyNextOffset)
        assertNull(state.historyFetchedAt)
        assertNull(state.historyError)
    }

    @Test
    fun `week bounds always begin on Monday and end on Sunday`() {
        // Wednesday Oct 14, 2026
        val state = PayrollUiState(referenceDate = LocalDate.of(2026, 10, 14))

        assertEquals(DayOfWeek.MONDAY, state.weekStartMonday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 12), state.weekStartMonday)

        assertEquals(DayOfWeek.SUNDAY, state.weekEndSunday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 18), state.weekEndSunday)
    }

    @Test
    fun `progressFraction calculates percentage liquidated accurately`() {
        // Null summary -> 0
        assertEquals(0f, PayrollUiState().progressFraction)

        // Zero total -> 0
        val zeroTotal = WeeklyPayrollSummary(
            periodoInicio = "2026-10-12",
            pagadoCents = 0L,
            pendienteCents = 0L,
            items = emptyList(),
        )
        assertEquals(0f, PayrollUiState(summary = zeroTotal).progressFraction)

        // 50% paid
        val halfPaid = WeeklyPayrollSummary(
            periodoInicio = "2026-10-12",
            pagadoCents = 150000L,
            pendienteCents = 150000L,
            items = listOf(sampleItem),
        )
        assertEquals(0.5f, PayrollUiState(summary = halfPaid).progressFraction, 0.001f)

        // 100% paid
        val fullPaid = WeeklyPayrollSummary(
            periodoInicio = "2026-10-12",
            pagadoCents = 150000L,
            pendienteCents = 0L,
            items = listOf(sampleItem),
        )
        assertEquals(1.0f, PayrollUiState(summary = fullPaid).progressFraction, 0.001f)
    }

    @Test
    fun `history pagination data page holds records and handles next offset`() {
        val page = DataPage(
            items = listOf(samplePayment),
            nextOffset = 1,
            hasMore = true,
            scope = "payroll-history",
            fetchedAt = Instant.now(),
        )

        assertEquals(1, page.items.size)
        assertEquals(1, page.nextOffset)
        assertTrue(page.hasMore)
        assertEquals("pay-1", page.items.first().id)
        assertEquals(150000L, page.items.first().montoCents)
        assertEquals(PaymentSyncStatus.CONFIRMED, page.items.first().syncStatus)
    }
}
