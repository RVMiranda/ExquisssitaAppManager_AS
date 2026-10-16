package com.mirandadevsource.exquisssitaappmanager.features.dashboard

data class LowStockProductItem(
    val id: String,
    val name: String,
    val currentStock: Int,
    val minStock: Int,
    val category: String,
)

data class EmployeePayItem(
    val id: String,
    val name: String,
    val wageCents: Long,
    val dayName: String,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val freeTables: Int = 0,
    val totalTables: Int = 0,
    val todayOrdersCount: Int = 0,
    val openOrdersCount: Int = 0,
    val todaySalesCents: Long = 0L,
    val todayPaymentsCount: Int = 0,
    val todayVisitsCount: Int = 0,
    val lowStockProducts: List<LowStockProductItem> = emptyList(),
    val employeesToPayToday: List<EmployeePayItem> = emptyList(),
    val pendingSyncCount: Int = 0,
    val userMessage: String? = null,
    val errorMessage: String? = null,
) {
    val tableAvailabilityLabel: String
        get() = if (totalTables > 0) "$freeTables / $totalTables" else "$freeTables"

    val hasLowStockAlert: Boolean
        get() = lowStockProducts.isNotEmpty()

    val hasPayrollAlert: Boolean
        get() = employeesToPayToday.isNotEmpty()

    val hasPendingSyncAlert: Boolean
        get() = pendingSyncCount > 0
}
