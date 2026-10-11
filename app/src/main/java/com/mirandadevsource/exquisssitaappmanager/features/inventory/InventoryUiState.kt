package com.mirandadevsource.exquisssitaappmanager.features.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import java.util.Locale

enum class ProductStatusFilter(val label: String) {
    ACTIVOS("Activos"),
    TODOS("Todos"),
    INACTIVOS("Inactivos"),
}

enum class StockFilter(val label: String) {
    TODOS("Todos"),
    BAJO_STOCK("Bajo Stock"),
    CONTROLADOS("Con Control"),
}

data class InventoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val rawProducts: List<Product> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val statusFilter: ProductStatusFilter = ProductStatusFilter.ACTIVOS,
    val stockFilter: StockFilter = StockFilter.TODOS,
) {
    val availableCategories: List<String>
        get() = rawProducts
            .map { it.categoria.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    val lowStockCount: Int
        get() = rawProducts.count {
            it.activo && it.controlaInventario && (it.stockActual ?: 0) <= (it.stockMinimo ?: 0)
        }

    val totalControlledCount: Int
        get() = rawProducts.count { it.activo && it.controlaInventario }

    val filteredProducts: List<Product>
        get() = rawProducts.filter { product ->
            val matchesStatus = when (statusFilter) {
                ProductStatusFilter.ACTIVOS -> product.activo
                ProductStatusFilter.TODOS -> true
                ProductStatusFilter.INACTIVOS -> !product.activo
            }

            val matchesCategory = selectedCategory == null ||
                product.categoria.equals(selectedCategory, ignoreCase = true)

            val matchesStock = when (stockFilter) {
                StockFilter.TODOS -> true
                StockFilter.BAJO_STOCK -> product.controlaInventario && (product.stockActual ?: 0) <= (product.stockMinimo ?: 0)
                StockFilter.CONTROLADOS -> product.controlaInventario
            }

            val query = searchQuery.trim()
            val matchesQuery = query.isBlank() ||
                product.nombre.contains(query, ignoreCase = true) ||
                product.categoria.contains(query, ignoreCase = true)

            matchesStatus && matchesCategory && matchesStock && matchesQuery
        }

    companion object {
        fun formatPrice(centavos: Long): String {
            val pesos = centavos / 100.0
            return "$${String.format(Locale.US, "%,.2f", pesos)} MXN"
        }
    }
}
