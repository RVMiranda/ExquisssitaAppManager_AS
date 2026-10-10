package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class WeeklyPayrollItem(
    val employee: Employee,
    val payment: EmployeePayment?,
    val pendingPayment: EmployeePayment? = null,
) {
    val isPaid: Boolean get() = payment?.syncStatus == PaymentSyncStatus.CONFIRMED
    val hasPendingPayment: Boolean get() = pendingPayment?.syncStatus == PaymentSyncStatus.PENDING_SYNC
}

data class WeeklyPayrollSummary(
    val periodoInicio: String,
    val items: List<WeeklyPayrollItem>,
    val pagadoCents: Long,
    val pendienteCents: Long,
) {
    val totalCents: Long get() = Math.addExact(pagadoCents, pendienteCents)
    val pendienteSyncCents: Long
        get() = items.mapNotNull { it.pendingPayment?.montoCents }.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
}
