package com.mirandadevsource.exquisssitaappmanager.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.GetProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { current ->
                if (isRefresh) current.copy(isRefreshing = true, errorMessage = null)
                else current.copy(isLoading = true, errorMessage = null)
            }

            getProductsUseCase(includeInactive = true)
                .onSuccess { products ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            rawProducts = products,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "Error al cargar el catálogo de productos.",
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelect(category: String?) {
        _uiState.update { current ->
            val newCategory = if (current.selectedCategory == category) null else category
            current.copy(selectedCategory = newCategory)
        }
    }

    fun onStatusFilterSelect(filter: ProductStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun onStockFilterSelect(filter: StockFilter) {
        _uiState.update { it.copy(stockFilter = filter) }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                selectedCategory = null,
                statusFilter = ProductStatusFilter.ACTIVOS,
                stockFilter = StockFilter.TODOS,
            )
        }
    }
}
