package com.mirandadevsource.exquisssitaappmanager.features.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollSummary
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PayrollUiState(
    val referenceDate: LocalDate = LocalDate.now(),
    val summary: WeeklyPayrollSummary? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val paymentModalItem: WeeklyPayrollItem? = null,
    val paymentDateInput: String = LocalDate.now().toString(),
    val paymentNotesInput: String = "",
    val isRegisteringPayment: Boolean = false,
    // Deduccion de Prestamos en Nomina
    val activeLoanForPayment: EmployeeLoan? = null,
    val loanDeductionPesosText: String = "",
    val isLoadingLoanForPayment: Boolean = false,
    val loanDeductionError: String? = null,
    // Historial
    val viewingHistoryEmployee: WeeklyPayrollItem? = null,
    val historyPayments: List<EmployeePayment> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val isLoadingHistoryPage: Boolean = false,
    val historyHasMore: Boolean = false,
    val historyNextOffset: Int? = null,
    val historyFetchedAt: Instant? = null,
    val historyError: String? = null,
) {
    val weekStartMonday: LocalDate
        get() = referenceDate.with(DayOfWeek.MONDAY)

    val weekEndSunday: LocalDate
        get() = weekStartMonday.plusDays(6)

    val formattedWeekRange: String
        get() {
            val startDay = weekStartMonday.dayOfMonth
            val endDay = weekEndSunday.dayOfMonth
            val locale = Locale.forLanguageTag("es-MX")
            val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
            val monthYear = weekEndSunday.format(monthFormatter)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            return "Semana: $startDay - $endDay de $monthYear"
        }

    val progressFraction: Float
        get() {
            val sum = summary ?: return 0f
            val total = sum.totalCents
            if (total <= 0L) return 0f
            return (sum.pagadoCents.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val deductionCents: Long
        get() {
            if (loanDeductionPesosText.isBlank() || activeLoanForPayment == null) return 0L
            return try {
                val normalized = loanDeductionPesosText.trim().replace("$", "").replace(",", "")
                val dec = BigDecimal(normalized)
                if (dec <= BigDecimal.ZERO) 0L else dec.movePointRight(2).longValueExact()
            } catch (_: Exception) {
                0L
            }
        }

    val netPayableCents: Long
        get() {
            val gross = paymentModalItem?.employee?.salarioCents ?: 0L
            return maxOf(0L, gross - deductionCents)
        }

    val netPayablePesos: BigDecimal
        get() = BigDecimal.valueOf(netPayableCents, 2)
}
