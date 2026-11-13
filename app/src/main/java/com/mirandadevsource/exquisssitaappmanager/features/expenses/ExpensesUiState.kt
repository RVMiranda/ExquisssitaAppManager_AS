package com.mirandadevsource.exquisssitaappmanager.features.expenses

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import java.time.LocalDate
import java.time.ZoneId

enum class ExpenseViewTab {
    GASTOS,
    PLANTILLAS,
}

data class ExpensesUiState(
    val selectedTab: ExpenseViewTab = ExpenseViewTab.GASTOS,
    val selectedDate: String = LocalDate.now(BUSINESS_ZONE).toString(),
    val expenses: List<Expense> = emptyList(),
    val templates: List<ExpenseTemplate> = emptyList(),
    val allTemplates: List<ExpenseTemplate> = emptyList(),
    val profitSummary: DailyProfitSummary? = null,
    val selectedCategoryFilter: String? = null,
    val selectedTemplateCategoryFilter: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingTemplate: Boolean = false,
    val isOnline: Boolean = true,
    val userRole: UserRole? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isFormDialogOpen: Boolean = false,
    val editingExpense: Expense? = null,
    val expenseToDelete: Expense? = null,
    val isTemplateDialogOpen: Boolean = false,
    val editingTemplate: ExpenseTemplate? = null,
    val templateToDelete: ExpenseTemplate? = null,
) {
    val isToday: Boolean
        get() = selectedDate == LocalDate.now(BUSINESS_ZONE).toString()

    val canManageExpenses: Boolean
        get() = userRole == UserRole.ADMIN || userRole == UserRole.GERENTE

    val filteredExpenses: List<Expense>
        get() = if (selectedCategoryFilter.isNullOrBlank()) {
            expenses
        } else {
            expenses.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }

    val filteredTemplates: List<ExpenseTemplate>
        get() = if (selectedTemplateCategoryFilter.isNullOrBlank()) {
            allTemplates
        } else {
            allTemplates.filter { it.category.equals(selectedTemplateCategoryFilter, ignoreCase = true) }
        }

    val totalExpensesCents: Long
        get() = expenses.sumOf { it.amountCents }

    val cashExpensesCents: Long
        get() = expenses.filter { it.paymentMethod == ExpensePaymentMethod.EFECTIVO }.sumOf { it.amountCents }

    val otherExpensesCents: Long
        get() = totalExpensesCents - cashExpensesCents

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
    }
}
