package com.mirandadevsource.exquisssitaappmanager.domain.entity

/**
 * Operación de egreso operativo diario. Montos monetarios siempre en centavos enteros MXN.
 */
data class Expense(
    val localId: String,
    val remoteId: String? = null,
    val date: String, // YYYY-MM-DD
    val concept: String,
    val category: String = ExpenseCategories.INSUMOS,
    val amountCents: Long,
    val quantity: Double? = null,
    val unitPriceCents: Long? = null,
    val paymentMethod: ExpensePaymentMethod = ExpensePaymentMethod.EFECTIVO,
    val templateId: String? = null,
    val notes: String? = null,
    val registeredBy: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncVersion: Long = 1L,
    val isPendingSync: Boolean = false,
)

enum class ExpensePaymentMethod(val rawValue: String, val displayName: String) {
    EFECTIVO("Efectivo", "Efectivo"),
    TRANSFERENCIA("Transferencia", "Transferencia"),
    OTRO("Otro", "Otro");

    companion object {
        fun fromRaw(value: String?): ExpensePaymentMethod =
            values().firstOrNull { it.rawValue.equals(value, ignoreCase = true) } ?: EFECTIVO
    }
}

object ExpenseCategories {
    const val INSUMOS = "Insumos"
    const val SERVICIOS = "Servicios"
    const val MANTENIMIENTO = "Mantenimiento"
    const val DESECHABLES = "Desechables"
    const val OTRO = "Otro"

    val ALL = listOf(INSUMOS, SERVICIOS, MANTENIMIENTO, DESECHABLES, OTRO)
}

/**
 * Plantilla o concepto de gasto frecuente con precio preferencial / unitario sugerido.
 */
data class ExpenseTemplate(
    val localId: String,
    val remoteId: String? = null,
    val name: String,
    val category: String = ExpenseCategories.INSUMOS,
    val unitPriceCents: Long,
    val unitOfMeasure: String = "kg",
    val isActive: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncVersion: Long = 1L,
) {
    val id: String
        get() = remoteId ?: localId
}

/**
 * Resumen de rentabilidad diaria contrastando ventas brutas, egresos operativos y nómina pagada.
 */
data class DailyProfitSummary(
    val date: String,
    val totalSalesCents: Long,
    val cashSalesCents: Long,
    val cardSalesCents: Long,
    val otherSalesCents: Long,
    val closedOrdersCount: Int,
    val totalExpensesCents: Long,
    val cashExpensesCents: Long,
    val otherExpensesCents: Long,
    val registeredExpensesCount: Int,
    val totalPayrollCents: Long,
    val payrollPaymentsCount: Int,
    val totalOutflowsCents: Long,
    val netProfitCents: Long,
    val expectedCashInDrawerCents: Long,
)
