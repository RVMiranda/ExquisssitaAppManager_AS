package com.mirandadevsource.exquisssitaappmanager.domain.usecases.payroll

import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentSyncStatus
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetWeeklyPayrollStatusUseCase @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    private val payrollRepository: PayrollRepository,
) {
    suspend operator fun invoke(referenceDate: LocalDate = LocalDate.now(MEXICO_ZONE)): Result<WeeklyPayrollSummary> {
        val weekStart = referenceDate.with(java.time.DayOfWeek.MONDAY).toString()
        val employees = employeeRepository.getEmployees(includeInactive = false)
            .getOrElse { return Result.failure(it) }
        val payments = payrollRepository.getPaymentsForPeriod(weekStart)
            .getOrElse { return Result.failure(it) }
        val paymentsByEmployee = payments.groupBy { it.empleadoId }
        val items = employees.map { employee ->
            val employeePayments = paymentsByEmployee[employee.id].orEmpty()
            WeeklyPayrollItem(
                employee = employee,
                payment = employeePayments.firstOrNull { it.syncStatus == PaymentSyncStatus.CONFIRMED },
                pendingPayment = employeePayments.firstOrNull { it.syncStatus == PaymentSyncStatus.PENDING_SYNC },
            )
        }
        return try {
            val paid = items.mapNotNull { it.payment?.montoCents }.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
            val pending = items.filterNot { it.isPaid }.map { it.employee.salarioCents }
                .fold(0L) { sum, amount -> Math.addExact(sum, amount) }
            Result.success(WeeklyPayrollSummary(weekStart, items, paid, pending))
        } catch (overflow: ArithmeticException) {
            Result.failure(overflow)
        }
    }

    private companion object {
        val MEXICO_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
