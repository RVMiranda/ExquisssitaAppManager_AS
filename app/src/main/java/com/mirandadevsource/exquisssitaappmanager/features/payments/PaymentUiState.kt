package com.mirandadevsource.exquisssitaappmanager.features.payments

import com.mirandadevsource.exquisssitaappmanager.domain.entity.PesoAmountParser
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table

/**
 * Modos de cobro y división de cuenta disponibles para comensales.
 */
enum class SplitMode(val label: String, val description: String) {
    FULL(
        label = "Total",
        description = "Cobro del saldo pendiente total de la orden",
    ),
    BY_ITEMS(
        label = "Por Partidas",
        description = "Selecciona artículos y unidades específicas a pagar",
    ),
    EQUAL_SPLIT(
        label = "Partes Iguales",
        description = "Divide la cuenta equitativamente entre varias personas",
    ),
}

/**
 * Estado inmutable de la pantalla de Cobro y Cuentas Divididas (Fase 3).
 */
data class PaymentUiState(
    val order: Order? = null,
    val table: Table? = null,
    val details: List<OrderDetail> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val productMap: Map<String, Product> = emptyMap(),
    val splitMode: SplitMode = SplitMode.FULL,
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
    val selectedItemQuantities: Map<String, Int> = emptyMap(),
    val splitCount: Int = 2,
    val cashReceivedInput: String = "",
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val lastPaymentCommand: PaymentCommand? = null,
) {
    /** Total acumulado de la comanda en centavos (excluyendo partidas canceladas) */
    val totalOrderCents: Long
        get() = details
            .filter { it.paymentStatus != PaymentLineStatus.CANCELLED }
            .sumOf { it.lineTotalCents }

    /** Monto pagado histórico acumulado por una partida específica */
    fun linePaidCents(detailId: String): Long {
        val line = details.firstOrNull { it.id == detailId } ?: return 0L
        if (line.paymentStatus == PaymentLineStatus.PAID) return line.lineTotalCents
        val allocated = payments
            .filter { it.status == PaymentStatus.CONFIRMED }
            .flatMap { it.allocations }
            .filter { it.orderDetailId == detailId }
            .sumOf { it.amountCents }
        return minOf(allocated, line.lineTotalCents)
    }

    /** Saldo monetario pendiente de una partida específica */
    fun linePendingCents(detailId: String): Long {
        val line = details.firstOrNull { it.id == detailId } ?: return 0L
        if (line.paymentStatus == PaymentLineStatus.CANCELLED) return 0L
        return (line.lineTotalCents - linePaidCents(detailId)).coerceAtLeast(0L)
    }

    /** Cantidad de unidades físicas pendientes de pago para una partida */
    fun lineRemainingQuantity(detailId: String): Int {
        val line = details.firstOrNull { it.id == detailId } ?: return 0
        if (line.paymentStatus == PaymentLineStatus.CANCELLED || line.paymentStatus == PaymentLineStatus.PAID) return 0
        val pending = linePendingCents(detailId)
        if (pending <= 0L || line.unitPriceCents <= 0L) return 0
        val units = ((pending + line.unitPriceCents - 1) / line.unitPriceCents).toInt()
        return units.coerceIn(0, line.quantity)
    }

    /** Unidades de stock ya deducidas para una partida */
    fun lineStockAllocated(detailId: String): Int {
        return payments
            .filter { it.status == PaymentStatus.CONFIRMED }
            .flatMap { it.allocations }
            .filter { it.orderDetailId == detailId }
            .sumOf { it.stockQuantity }
    }

    /** Unidades de stock pendientes de deducir (0 si el producto no controla inventario) */
    fun lineRemainingStock(detailId: String): Int {
        val line = details.firstOrNull { it.id == detailId } ?: return 0
        val product = productMap[line.productId]
        if (product?.controlaInventario != true) return 0
        return (line.quantity - lineStockAllocated(detailId)).coerceAtLeast(0)
    }

    /** Monto ya cubierto en centavos considerando pagos confirmados y partidas pagadas */
    val paidAmountCents: Long
        get() {
            val fromPayments = payments
                .filter { it.status == PaymentStatus.CONFIRMED }
                .sumOf { it.amountCents }
            val fromLines = details
                .filter { it.paymentStatus == PaymentLineStatus.PAID }
                .sumOf { it.lineTotalCents }
            return maxOf(fromPayments, fromLines).coerceAtMost(totalOrderCents)
        }

    /** Saldo restante pendiente de pago de la comanda */
    val pendingBalanceCents: Long
        get() = (totalOrderCents - paidAmountCents).coerceAtLeast(0L)

    /** Partidas de la orden que aún tienen saldo monetario pendiente por liquidar */
    val pendingDetails: List<OrderDetail>
        get() = details.filter {
            it.paymentStatus != PaymentLineStatus.CANCELLED &&
                it.paymentStatus != PaymentLineStatus.PAID &&
                linePendingCents(it.id) > 0L
        }

    /** Indica si la orden ya no tiene saldo pendiente */
    val isFullyPaid: Boolean
        get() = totalOrderCents > 0L && pendingBalanceCents == 0L

    /**
     * Calcula el monto a pagar en centavos según el modo de cobro seleccionado.
     */
    val calculatedPaymentAmountCents: Long
        get() = when (splitMode) {
            SplitMode.FULL -> pendingBalanceCents
            SplitMode.BY_ITEMS -> {
                pendingDetails.sumOf { item ->
                    val maxQty = lineRemainingQuantity(item.id)
                    val qtyToPay = (selectedItemQuantities[item.id] ?: 0).coerceIn(0, maxQty)
                    minOf(qtyToPay.toLong() * item.unitPriceCents, linePendingCents(item.id))
                }
            }
            SplitMode.EQUAL_SPLIT -> {
                if (splitCount <= 1 || pendingBalanceCents == 0L) {
                    pendingBalanceCents
                } else {
                    // Cuota por persona redondeada hacia arriba para no dejar centavos sueltos
                    val share = (pendingBalanceCents + splitCount - 1) / splitCount
                    share.coerceAtMost(pendingBalanceCents)
                }
            }
        }

    /** Efectivo recibido en centavos parseado desde el input */
    val cashReceivedCents: Long
        get() = PesoAmountParser.toCents(cashReceivedInput) ?: 0L

    val isCashReceivedInputValid: Boolean
        get() = cashReceivedInput.isBlank() || PesoAmountParser.toCents(cashReceivedInput) != null

    /** Cambio en centavos si se paga en efectivo y el monto entregado es mayor o igual */
    val cashChangeCents: Long
        get() {
            if (selectedMethod != PaymentMethod.CASH) return 0L
            val paymentAmount = calculatedPaymentAmountCents
            val received = cashReceivedCents
            return if (received >= paymentAmount) received - paymentAmount else 0L
        }

    /** Verifica si se puede procesar el pago */
    val canSubmitPayment: Boolean
        get() {
            if (isSubmitting || isLoading) return false
            if (order == null) return false
            val amount = calculatedPaymentAmountCents
            if (amount <= 0L || amount > pendingBalanceCents) return false
            if (selectedMethod == PaymentMethod.CASH) {
                if (cashReceivedInput.isBlank() || !isCashReceivedInputValid || cashReceivedCents < amount) return false
            }
            return true
        }
}
