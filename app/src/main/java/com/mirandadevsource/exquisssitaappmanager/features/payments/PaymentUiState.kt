package com.mirandadevsource.exquisssitaappmanager.features.payments

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
    /** Partidas de la orden que aún tienen saldo pendiente por pagar */
    val pendingDetails: List<OrderDetail>
        get() = details.filter { it.paymentStatus == PaymentLineStatus.PENDING }

    /** Total acumulado de la comanda en centavos (excluyendo partidas canceladas) */
    val totalOrderCents: Long
        get() = details
            .filter { it.paymentStatus != PaymentLineStatus.CANCELLED }
            .sumOf { it.lineTotalCents }

    /** Monto ya cubierto en centavos según las partidas marcadas como pagadas */
    val paidAmountCents: Long
        get() = details
            .filter { it.paymentStatus == PaymentLineStatus.PAID }
            .sumOf { it.lineTotalCents }

    /** Saldo restante pendiente de pago */
    val pendingBalanceCents: Long
        get() = (totalOrderCents - paidAmountCents).coerceAtLeast(0L)

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
                    val qtyToPay = (selectedItemQuantities[item.id] ?: 0).coerceIn(0, item.quantity)
                    qtyToPay.toLong() * item.unitPriceCents
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
        get() {
            val clean = cashReceivedInput.trim().replace("$", "").replace(",", "")
            val doubleVal = clean.toDoubleOrNull() ?: return 0L
            return (doubleVal * 100).toLong().coerceAtLeast(0L)
        }

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
            if (selectedMethod == PaymentMethod.CASH && cashReceivedInput.isNotBlank()) {
                if (cashReceivedCents < amount) return false
            }
            return true
        }
}
