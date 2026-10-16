package com.mirandadevsource.exquisssitaappmanager.features.cash_register

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import java.time.LocalDate
import java.time.ZoneId

data class CashRegisterUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val selectedDate: String = LocalDate.now(BUSINESS_ZONE).toString(),
    val cuts: List<DailyClosure> = emptyList(),
    val daySummary: CashRegisterDaySummary? = null,
    val openOrdersCount: Int = 0,
    val isOnline: Boolean = true,
    val userRole: UserRole? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showConfirmCutDialog: Boolean = false,
) {
    val isToday: Boolean
        get() = selectedDate == LocalDate.now(BUSINESS_ZONE).toString()

    val maxCutsReached: Boolean
        get() = cuts.size >= 5

    val nextCutNumber: Int
        get() = (cuts.size + 1).coerceAtMost(5)

    val canPerformCut: Boolean
        get() = isToday && isOnline && openOrdersCount == 0 && !maxCutsReached && (userRole == UserRole.ADMIN || userRole == UserRole.GERENTE)

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
