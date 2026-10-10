package com.mirandadevsource.exquisssitaappmanager.features.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollSummary
import java.time.DayOfWeek
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
}
