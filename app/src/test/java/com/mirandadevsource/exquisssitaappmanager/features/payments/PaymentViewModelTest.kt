package com.mirandadevsource.exquisssitaappmanager.features.payments

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentAllocation
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentViewModelTest {

    private val sampleOrder = Order(
        id = "ord-100",
        tableId = "tbl-1",
        customerId = null,
        serviceType = ServiceType.TABLE,
        status = OrderStatus.OPEN,
        openedAt = "2026-10-12T00:00:00Z",
        closedAt = null,
        notes = null,
        createdAt = "2026-10-12T00:00:00Z",
        updatedAt = "2026-10-12T00:00:00Z",
        syncVersion = 1,
    )

    private val sampleTable = Table(
        id = "tbl-1",
        number = "Mesa 1",
        status = TableStatus.OCCUPIED,
        openOrderCount = 1,
        createdAt = "2026-10-12T00:00:00Z",
        updatedAt = "2026-10-12T00:00:00Z",
        syncVersion = 1,
    )

    private val sampleProducts = listOf(
        Product(
            id = "p-1",
            nombre = "Tacos de Bistec",
            precioCentavos = 3000L,
            categoria = "Tacos",
            controlaInventario = true,
            stockActual = 20,
            stockMinimo = 5,
            activo = true,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        ),
        Product(
            id = "p-2",
            nombre = "Coca Cola",
            precioCentavos = 3000L,
            categoria = "Refrescos",
            controlaInventario = true,
            stockActual = 15,
            stockMinimo = 5,
            activo = true,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        ),
    )

    private val sampleDetails = listOf(
        OrderDetail(
            id = "det-1",
            orderId = "ord-100",
            productId = "p-1",
            quantity = 3,
            unitPriceCents = 3000L, // 3 * 3000 = 9000
            paymentStatus = PaymentLineStatus.PENDING,
            notes = null,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        ),
        OrderDetail(
            id = "det-2",
            orderId = "ord-100",
            productId = "p-2",
            quantity = 2,
            unitPriceCents = 3000L, // 2 * 3000 = 6000
            paymentStatus = PaymentLineStatus.PENDING,
            notes = null,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        ),
        OrderDetail(
            id = "det-3",
            orderId = "ord-100",
            productId = "p-1",
            quantity = 1,
            unitPriceCents = 3000L,
            paymentStatus = PaymentLineStatus.CANCELLED,
            notes = "Cancelado por error",
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        ),
    )

    @Test
    fun `PaymentUiState calculates total, pending, and paid balances excluding cancelled items`() {
        val state = PaymentUiState(
            order = sampleOrder,
            table = sampleTable,
            details = sampleDetails,
            productMap = sampleProducts.associateBy { it.id },
        )

        // det-1: 9000 PENDING, det-2: 6000 PENDING, det-3: CANCELLED (ignorado)
        assertEquals(15000L, state.totalOrderCents)
        assertEquals(0L, state.paidAmountCents)
        assertEquals(15000L, state.pendingBalanceCents)
        assertFalse(state.isFullyPaid)
    }

    @Test
    fun `PaymentUiState detects fully paid order`() {
        val paidDetails = listOf(
            OrderDetail(
                id = "det-1",
                orderId = "ord-100",
                productId = "p-1",
                quantity = 2,
                unitPriceCents = 2500L,
                paymentStatus = PaymentLineStatus.PAID,
                notes = null,
                createdAt = "2026-10-12T00:00:00Z",
                updatedAt = "2026-10-12T00:00:00Z",
                syncVersion = 1,
            ),
        )

        val state = PaymentUiState(
            order = sampleOrder,
            details = paidDetails,
        )

        assertEquals(5000L, state.totalOrderCents)
        assertEquals(5000L, state.paidAmountCents)
        assertEquals(0L, state.pendingBalanceCents)
        assertTrue(state.isFullyPaid)
    }

    @Test
    fun `SplitMode FULL calculates total pending balance as payment amount`() {
        val state = PaymentUiState(
            order = sampleOrder,
            table = sampleTable,
            details = sampleDetails,
            splitMode = SplitMode.FULL,
        )

        assertEquals(15000L, state.calculatedPaymentAmountCents)
        assertTrue(state.canSubmitPayment)
    }

    @Test
    fun `SplitMode BY_ITEMS calculates amount based on selected item quantities`() {
        val state = PaymentUiState(
            order = sampleOrder,
            table = sampleTable,
            details = sampleDetails,
            splitMode = SplitMode.BY_ITEMS,
            selectedItemQuantities = mapOf(
                "det-1" to 2, // 2 * 3000 = 6000
                "det-2" to 1, // 1 * 3000 = 3000
            ),
        )

        assertEquals(9000L, state.calculatedPaymentAmountCents)
        assertTrue(state.canSubmitPayment)

        // Sin selección
        val emptySelectionState = state.copy(selectedItemQuantities = emptyMap())
        assertEquals(0L, emptySelectionState.calculatedPaymentAmountCents)
        assertFalse(emptySelectionState.canSubmitPayment)
    }

    @Test
    fun `SplitMode EQUAL_SPLIT divides pending balance equitably between people`() {
        val state = PaymentUiState(
            order = sampleOrder,
            table = sampleTable,
            details = sampleDetails,
            splitMode = SplitMode.EQUAL_SPLIT,
            splitCount = 2,
        )

        // 15000 / 2 = 7500
        assertEquals(7500L, state.calculatedPaymentAmountCents)

        // 15000 / 3 = 5000
        val state3 = state.copy(splitCount = 3)
        assertEquals(5000L, state3.calculatedPaymentAmountCents)

        // 15000 / 4 = 3750
        val state4 = state.copy(splitCount = 4)
        assertEquals(3750L, state4.calculatedPaymentAmountCents)
    }

    @Test
    fun `CashCalculator parses cash input and computes change accurately`() {
        val state = PaymentUiState(
            order = sampleOrder,
            table = sampleTable,
            details = sampleDetails,
            splitMode = SplitMode.FULL,
            selectedMethod = PaymentMethod.CASH,
            cashReceivedInput = "200.00",
        )

        // Saldo total = 15000 cents ($150.00). Efectivo = $200.00 (20000 cents). Cambio = $50.00 (5000 cents).
        assertEquals(20000L, state.cashReceivedCents)
        assertEquals(5000L, state.cashChangeCents)
        assertTrue(state.canSubmitPayment)

        // Pago exacto
        val exactState = state.copy(cashReceivedInput = "150")
        assertEquals(15000L, exactState.cashReceivedCents)
        assertEquals(0L, exactState.cashChangeCents)
        assertTrue(exactState.canSubmitPayment)

        // Pago insuficiente
        val insufficientState = state.copy(cashReceivedInput = "100")
        assertEquals(10000L, insufficientState.cashReceivedCents)
        assertEquals(0L, insufficientState.cashChangeCents)
        assertFalse(insufficientState.canSubmitPayment)
    }

    @Test
    fun `PaymentUiState correctly calculates balances with partial payments`() {
        val line1 = OrderDetail(
            id = "det-1",
            orderId = "ord-100",
            productId = "p-1",
            quantity = 3,
            unitPriceCents = 3000L, // $90.00
            paymentStatus = PaymentLineStatus.PENDING,
            notes = null,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        )
        val line2 = OrderDetail(
            id = "det-2",
            orderId = "ord-100",
            productId = "p-2",
            quantity = 2,
            unitPriceCents = 3000L, // $60.00
            paymentStatus = PaymentLineStatus.PENDING,
            notes = null,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        )
        val payment1 = Payment(
            id = "pay-1",
            orderId = "ord-100",
            amountCents = 6000L,
            method = PaymentMethod.CASH,
            paidAt = "2026-10-12T01:00:00Z",
            idempotencyKey = "key-1",
            status = PaymentStatus.CONFIRMED,
            allocations = listOf(
                PaymentAllocation(
                    orderDetailId = "det-1",
                    quantity = 2,
                    amountCents = 6000L,
                    stockQuantity = 2,
                )
            ),
        )

        val state = PaymentUiState(
            order = sampleOrder,
            details = listOf(line1, line2),
            payments = listOf(payment1),
            productMap = sampleProducts.associateBy { it.id },
        )

        assertEquals(15000L, state.totalOrderCents)
        assertEquals(6000L, state.paidAmountCents)
        assertEquals(9000L, state.pendingBalanceCents)
        assertEquals(6000L, state.linePaidCents("det-1"))
        assertEquals(3000L, state.linePendingCents("det-1"))
        assertEquals(1, state.lineRemainingQuantity("det-1")) // 3 - 2 = 1 remaining unit
        assertEquals(0L, state.linePaidCents("det-2"))
        assertEquals(6000L, state.linePendingCents("det-2"))
        assertEquals(2, state.lineRemainingQuantity("det-2"))
        assertEquals(1, state.lineRemainingStock("det-1"))
        assertEquals(2, state.lineRemainingStock("det-2"))
    }

    @Test
    fun `Payment allocations for non-inventory products set stockQuantity to 0`() {
        val nonInventoryProduct = Product(
            id = "p-kitchen",
            nombre = "Orden de Tacos Preparados",
            precioCentavos = 4500L,
            categoria = "Tacos",
            controlaInventario = false,
            stockActual = null,
            stockMinimo = null,
            activo = true,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        )
        val kitchenLine = OrderDetail(
            id = "det-kitchen",
            orderId = "ord-100",
            productId = "p-kitchen",
            quantity = 2,
            unitPriceCents = 4500L,
            paymentStatus = PaymentLineStatus.PENDING,
            notes = null,
            createdAt = "2026-10-12T00:00:00Z",
            updatedAt = "2026-10-12T00:00:00Z",
            syncVersion = 1,
        )
        val products = mapOf(nonInventoryProduct.id to nonInventoryProduct)
        val state = PaymentUiState(
            order = sampleOrder,
            details = listOf(kitchenLine),
            productMap = products,
            splitMode = SplitMode.FULL,
        )

        assertEquals(0, state.lineRemainingStock("det-kitchen"))
    }

    @Test
    fun `FakePaymentRepository processes payment command successfully`() = runBlocking {
        val repo = FakePaymentRepository()

        val request = PaymentRequest(
            orderId = "ord-100",
            amountCents = 15000L,
            method = PaymentMethod.CASH,
            allocations = listOf(
                PaymentAllocation(
                    orderDetailId = "det-1",
                    quantity = 3,
                    amountCents = 9000L,
                    stockQuantity = 3,
                ),
                PaymentAllocation(
                    orderDetailId = "det-2",
                    quantity = 2,
                    amountCents = 6000L,
                    stockQuantity = 2,
                ),
            ),
        )

        val result = repo.processPayment(request)
        assertTrue(result.isSuccess)

        val command = result.getOrThrow()
        assertEquals("ord-100", command.orderId)
        assertEquals(PaymentStatus.CONFIRMED, command.status)
        assertNotNull(command.transactionId)

        val payments = repo.observePayments("ord-100").first()
        assertEquals(1, payments.size)
        assertEquals(15000L, payments.first().amountCents)
        assertEquals(PaymentMethod.CASH, payments.first().method)
    }

    @Test
    fun `FakePaymentRepository returns failure when shouldFail is set`() = runBlocking {
        val repo = FakePaymentRepository()
        repo.shouldFail = true

        val request = PaymentRequest(
            orderId = "ord-100",
            amountCents = 15000L,
            method = PaymentMethod.CARD,
            allocations = emptyList(),
        )

        val result = repo.processPayment(request)
        assertTrue(result.isFailure)
        assertEquals("Error forzado de pago.", result.exceptionOrNull()?.message)
    }

    // --- Fakes para pruebas ---

    private class FakePaymentRepository : PaymentRepository {
        private val paymentsFlow = MutableStateFlow<List<Payment>>(emptyList())
        var lastRequest: PaymentRequest? = null
        var shouldFail = false

        override fun observePayments(orderId: String): Flow<List<Payment>> = paymentsFlow

        override suspend fun processPayment(request: PaymentRequest): Result<PaymentCommand> {
            lastRequest = request
            if (shouldFail) {
                return Result.failure(IllegalStateException("Error forzado de pago."))
            }

            val newPayment = Payment(
                id = "pay-${System.currentTimeMillis()}",
                orderId = request.orderId,
                amountCents = request.amountCents,
                method = request.method,
                paidAt = "2026-10-12T15:00:00Z",
                idempotencyKey = "key-${System.currentTimeMillis()}",
                status = PaymentStatus.CONFIRMED,
                allocations = request.allocations,
            )
            paymentsFlow.value = paymentsFlow.value + newPayment

            return Result.success(
                PaymentCommand(
                    idempotencyKey = newPayment.idempotencyKey,
                    orderId = request.orderId,
                    status = PaymentStatus.CONFIRMED,
                    transactionId = newPayment.id,
                )
            )
        }

        override suspend fun syncPendingPayments(): Result<Unit> = Result.success(Unit)
    }
}
