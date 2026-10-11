package com.mirandadevsource.exquisssitaappmanager.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.CreateProductUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.GetInventoryMovementsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.GetProductsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.RegisterInventoryMovementUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.UpdateProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val registerInventoryMovementUseCase: RegisterInventoryMovementUseCase,
    private val getInventoryMovementsUseCase: GetInventoryMovementsUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        observeRole()
        loadProducts()
    }

    private fun observeRole() {
        viewModelScope.launch {
            authRepository.state.collect { state ->
                val isAdmin = (state as? AuthState.SignedIn)?.session?.role == UserRole.ADMIN
                _uiState.update { it.copy(isAdmin = isAdmin) }
            }
        }
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

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // --- Product Form Dialog Operations ---

    fun openCreateProductDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingProduct = null,
                formNombre = "",
                formPrecioPesos = "",
                formCategoria = "",
                formControlaInventario = false,
                formStockMinimo = "0",
                formActivo = true,
                formErrorMessage = null,
            )
        }
    }

    fun openEditProductDialog(product: Product) {
        val pesosStr = String.format(Locale.US, "%.2f", product.precioCentavos / 100.0)
        _uiState.update {
            it.copy(
                isFormDialogOpen = true,
                editingProduct = product,
                formNombre = product.nombre,
                formPrecioPesos = pesosStr,
                formCategoria = product.categoria,
                formControlaInventario = product.controlaInventario,
                formStockMinimo = (product.stockMinimo ?: 0).toString(),
                formActivo = product.activo,
                formErrorMessage = null,
            )
        }
    }

    fun closeProductDialog() {
        _uiState.update {
            it.copy(
                isFormDialogOpen = false,
                editingProduct = null,
                formErrorMessage = null,
                isSavingProduct = false,
            )
        }
    }

    fun onFormNombreChange(value: String) {
        _uiState.update { it.copy(formNombre = value, formErrorMessage = null) }
    }

    fun onFormPrecioPesosChange(value: String) {
        _uiState.update { it.copy(formPrecioPesos = value, formErrorMessage = null) }
    }

    fun onFormCategoriaChange(value: String) {
        _uiState.update { it.copy(formCategoria = value, formErrorMessage = null) }
    }

    fun onFormControlaInventarioChange(value: Boolean) {
        _uiState.update { it.copy(formControlaInventario = value) }
    }

    fun onFormStockMinimoChange(value: String) {
        _uiState.update { it.copy(formStockMinimo = value, formErrorMessage = null) }
    }

    fun onFormActivoChange(value: Boolean) {
        _uiState.update { it.copy(formActivo = value) }
    }

    fun saveProduct() {
        val state = _uiState.value
        val nombre = state.formNombre.trim()
        if (nombre.isBlank()) {
            _uiState.update { it.copy(formErrorMessage = "El nombre del producto no puede estar vacío.") }
            return
        }

        val categoria = state.formCategoria.trim()
        if (categoria.isBlank()) {
            _uiState.update { it.copy(formErrorMessage = "La categoría es obligatoria.") }
            return
        }

        val centavos = InventoryUiState.parsePesosToCentavos(state.formPrecioPesos)
        if (centavos == null || centavos < 0) {
            _uiState.update { it.copy(formErrorMessage = "Ingresa un precio válido en pesos (ej. 35.00).") }
            return
        }

        val stockMinimo = if (state.formControlaInventario) {
            state.formStockMinimo.toIntOrNull() ?: run {
                _uiState.update { it.copy(formErrorMessage = "El stock mínimo debe ser un número entero.") }
                return
            }
        } else 0

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProduct = true, formErrorMessage = null) }

            val result = if (state.editingProduct == null) {
                createProductUseCase(
                    nombre = nombre,
                    precioCentavos = centavos,
                    categoria = categoria,
                    controlaInventario = state.formControlaInventario,
                    stockMinimo = stockMinimo,
                )
            } else {
                updateProductUseCase(
                    id = state.editingProduct.id,
                    nombre = nombre,
                    precioCentavos = centavos,
                    categoria = categoria,
                    controlaInventario = state.formControlaInventario,
                    stockMinimo = stockMinimo,
                    activo = state.formActivo,
                )
            }

            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isSavingProduct = false,
                        isFormDialogOpen = false,
                        editingProduct = null,
                        userMessage = if (state.editingProduct == null) "Producto creado exitosamente." else "Producto actualizado exitosamente.",
                    )
                }
                loadProducts(isRefresh = true)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isSavingProduct = false,
                        formErrorMessage = error.localizedMessage ?: "Error al guardar el producto.",
                    )
                }
            }
        }
    }

    // --- Stock Movement Dialog Operations ---

    fun openMovementDialog(
        product: Product,
        defaultType: InventoryMovementType = InventoryMovementType.ENTRADA,
    ) {
        _uiState.update {
            it.copy(
                isMovementDialogOpen = true,
                movementProduct = product,
                movementType = defaultType,
                movementQuantity = "",
                movementNotas = "",
                movementErrorMessage = null,
                isSubmittingMovement = false,
            )
        }
    }

    fun closeMovementDialog() {
        _uiState.update {
            it.copy(
                isMovementDialogOpen = false,
                movementProduct = null,
                movementErrorMessage = null,
                isSubmittingMovement = false,
            )
        }
    }

    fun onMovementTypeChange(type: InventoryMovementType) {
        _uiState.update { it.copy(movementType = type, movementErrorMessage = null) }
    }

    fun onMovementQuantityChange(quantity: String) {
        _uiState.update { it.copy(movementQuantity = quantity, movementErrorMessage = null) }
    }

    fun onMovementNotasChange(notas: String) {
        _uiState.update { it.copy(movementNotas = notas) }
    }

    fun submitStockMovement() {
        val state = _uiState.value
        val product = state.movementProduct ?: return
        val rawQty = state.movementQuantity.trim().toIntOrNull()

        if (rawQty == null || rawQty == 0) {
            _uiState.update { it.copy(movementErrorMessage = "Ingresa una cantidad válida mayor a cero.") }
            return
        }

        val type = state.movementType
        val signedQty = when (type) {
            InventoryMovementType.ENTRADA -> rawQty.coerceAtLeast(1)
            InventoryMovementType.MERMA -> -rawQty.coerceAtLeast(1)
            InventoryMovementType.AJUSTE -> rawQty // can be positive or negative delta
            else -> rawQty
        }

        val expectedVersion = if (type == InventoryMovementType.AJUSTE) product.syncVersion else null

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingMovement = true, movementErrorMessage = null) }

            val request = RegisterInventoryMovement(
                productId = product.id,
                quantity = signedQty,
                type = type,
                referenceId = null,
                notes = state.movementNotas.trim().ifBlank { null },
                expectedVersion = expectedVersion,
            )

            registerInventoryMovementUseCase(request)
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isSubmittingMovement = false,
                            isMovementDialogOpen = false,
                            movementProduct = null,
                            userMessage = "Movimiento de stock registrado.",
                        )
                    }
                    loadProducts(isRefresh = true)
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isSubmittingMovement = false,
                            movementErrorMessage = error.localizedMessage ?: "Error al registrar movimiento.",
                        )
                    }
                }
        }
    }

    // --- Kardex History Dialog Operations ---

    fun openKardexDialog(product: Product) {
        _uiState.update {
            it.copy(
                isKardexDialogOpen = true,
                kardexProduct = product,
                kardexMovements = emptyList(),
                isLoadingKardex = true,
                kardexErrorMessage = null,
            )
        }

        viewModelScope.launch {
            getInventoryMovementsUseCase(product.id, offset = 0, limit = 50)
                .onSuccess { movements ->
                    _uiState.update { current ->
                        current.copy(
                            isLoadingKardex = false,
                            kardexMovements = movements,
                            kardexErrorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isLoadingKardex = false,
                            kardexErrorMessage = error.localizedMessage ?: "Error al cargar movimientos de kardex.",
                        )
                    }
                }
        }
    }

    fun closeKardexDialog() {
        _uiState.update {
            it.copy(
                isKardexDialogOpen = false,
                kardexProduct = null,
                kardexMovements = emptyList(),
                kardexErrorMessage = null,
            )
        }
    }
}
