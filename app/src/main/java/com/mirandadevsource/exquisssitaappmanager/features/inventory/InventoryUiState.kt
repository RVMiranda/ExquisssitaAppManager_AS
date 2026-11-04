package com.mirandadevsource.exquisssitaappmanager.features.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
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
    val userMessage: String? = null,
    val rawProducts: List<Product> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val statusFilter: ProductStatusFilter = ProductStatusFilter.ACTIVOS,
    val stockFilter: StockFilter = StockFilter.TODOS,
    val isAdmin: Boolean = false,
    val isLoadingPage: Boolean = false,
    val hasMore: Boolean = false,
    val nextOffset: Int? = null,
    val pageFetchedAt: java.time.Instant? = null,

    // Product Create / Edit Form State
    val isFormDialogOpen: Boolean = false,
    val editingProduct: Product? = null,
    val formNombre: String = "",
    val formPrecioPesos: String = "",
    val formCategoria: String = "",
    val formControlaInventario: Boolean = false,
    val formPrecioAbierto: Boolean = false,
    val formStockMinimo: String = "0",
    val formActivo: Boolean = true,
    val isSavingProduct: Boolean = false,
    val formErrorMessage: String? = null,

    // Stock Movement Dialog State
    val isMovementDialogOpen: Boolean = false,
    val movementProduct: Product? = null,
    val movementType: InventoryMovementType = InventoryMovementType.ENTRADA,
    val movementQuantity: String = "",
    val movementNotas: String = "",
    val isSubmittingMovement: Boolean = false,
    val movementErrorMessage: String? = null,

    // Kardex History Dialog State
    val isKardexDialogOpen: Boolean = false,
    val kardexProduct: Product? = null,
    val kardexMovements: List<StockMovement> = emptyList(),
    val isLoadingKardex: Boolean = false,
    val kardexErrorMessage: String? = null,
) {
    val availableCategories: List<String>
        get() = rawProducts
            .map { it.categoria.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    val formCategoryOptions: List<String>
        get() {
            val fromProducts = rawProducts
                .map { it.categoria.trim() }
                .filter { it.isNotBlank() }
            val extra = fromProducts.filter { prodCat ->
                DEFAULT_PRODUCT_CATEGORIES.none { it.equals(prodCat, ignoreCase = true) }
            }.distinct().sorted()
            return DEFAULT_PRODUCT_CATEGORIES + extra
        }

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
        val DEFAULT_PRODUCT_CATEGORIES = listOf(
            "Refrescos",
            "Jugos",
            "Aguas N",
            "Tacos",
            "Gorditas",
            "Carne",
            "Verdura",
            "Otros",
        )

        fun formatPrice(centavos: Long): String {
            val pesos = centavos / 100.0
            return "$${String.format(Locale.US, "%,.2f", pesos)} MXN"
        }

        fun parsePesosToCentavos(input: String): Long? {
            val clean = input.trim().replace("$", "").replace(",", "")
            if (clean.isBlank()) return null
            val parts = clean.split(".")
            if (parts.size > 2) return null
            val enteros = parts[0].toLongOrNull() ?: return null
            if (enteros < 0) return null
            val decimales = if (parts.size == 2) {
                val decStr = parts[1]
                when (decStr.length) {
                    0 -> 0L
                    1 -> (decStr + "0").toLongOrNull() ?: return null
                    2 -> decStr.toLongOrNull() ?: return null
                    else -> return null
                }
            } else 0L
            return enteros * 100L + decimales
        }
    }
}
