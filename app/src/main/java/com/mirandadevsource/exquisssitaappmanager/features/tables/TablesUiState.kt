package com.mirandadevsource.exquisssitaappmanager.features.tables

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus

enum class TableStatusFilter(val label: String) {
    TODAS("Todas"),
    LIBRES("Libres"),
    OCUPADAS("Ocupadas"),
    RESERVADAS("Reservadas"),
}

data class TablesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val rawTables: List<Table> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: TableStatusFilter = TableStatusFilter.TODAS,

    // Diálogo "Nueva Mesa"
    val isCreateTableDialogOpen: Boolean = false,
    val newTableNumber: String = "",
    val isSavingTable: Boolean = false,
    val createTableError: String? = null,

    // Diálogo "Detalle de Mesa"
    val selectedTable: Table? = null,
    val isTableDetailDialogOpen: Boolean = false,
    val tableOrders: List<Order> = emptyList(),
    val isLoadingOrders: Boolean = false,
    val isTogglingReservation: Boolean = false,
    val detailActionError: String? = null,
) {
    val totalCount: Int
        get() = rawTables.size

    val freeCount: Int
        get() = rawTables.count { it.status == TableStatus.FREE }

    val occupiedCount: Int
        get() = rawTables.count { it.status == TableStatus.OCCUPIED }

    val reservedCount: Int
        get() = rawTables.count { it.status == TableStatus.RESERVED }

    val filteredTables: List<Table>
        get() {
            val query = searchQuery.trim()
            return rawTables
                .filter { table ->
                    when (statusFilter) {
                        TableStatusFilter.TODAS -> true
                        TableStatusFilter.LIBRES -> table.status == TableStatus.FREE
                        TableStatusFilter.OCUPADAS -> table.status == TableStatus.OCCUPIED
                        TableStatusFilter.RESERVADAS -> table.status == TableStatus.RESERVED
                    }
                }
                .filter { table ->
                    if (query.isBlank()) true
                    else table.number.contains(query, ignoreCase = true)
                }
                .sortedWith(
                    compareBy<Table> {
                        // Ordenar numéricamente si es posible (ej. "Mesa 1", "Mesa 2", "1", "2")
                        val digits = it.number.filter { ch -> ch.isDigit() }
                        digits.toIntOrNull() ?: Int.MAX_VALUE
                    }.thenBy { it.number.lowercase() }
                )
        }
}
