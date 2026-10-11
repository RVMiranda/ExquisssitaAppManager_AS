package com.mirandadevsource.exquisssitaappmanager.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ErrorRed
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

private val SuccessGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.loadProducts(isRefresh = true) },
        modifier = modifier
            .fillMaxSize()
            .background(VanillaCustard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // Header con título, botón de refrescar y botón de Nuevo Producto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Menú e Inventario",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Catálogo de productos, precios y control de existencias",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.loadProducts(isRefresh = true) },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refrescar catálogo",
                            tint = BrandyBrown,
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.openCreateProductDialog() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, BrandyBrown),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Nuevo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Mensajes informativos de usuario
            uiState.userMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.dismissUserMessage() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cerrar",
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Buscador
            InventorySearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            // KPI Banner
            InventoryKpiBanner(
                totalCount = uiState.rawProducts.size,
                controlledCount = uiState.totalControlledCount,
                lowStockCount = uiState.lowStockCount,
                isLowStockFilterActive = uiState.stockFilter == StockFilter.BAJO_STOCK,
                onToggleLowStockFilter = {
                    val nextFilter = if (uiState.stockFilter == StockFilter.BAJO_STOCK) {
                        StockFilter.TODOS
                    } else {
                        StockFilter.BAJO_STOCK
                    }
                    viewModel.onStockFilterSelect(nextFilter)
                },
            )

            Spacer(Modifier.height(10.dp))

            // Chips horizontales de categorías
            CategoryChipRow(
                categories = uiState.availableCategories,
                selectedCategory = uiState.selectedCategory,
                onSelectCategory = viewModel::onCategorySelect,
            )

            Spacer(Modifier.height(10.dp))

            // Filtros de estado (Activos / Todos / Inactivos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProductStatusFilter.entries.forEach { filter ->
                    val isSelected = uiState.statusFilter == filter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandyBrown else Color.White.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.onStatusFilterSelect(filter) },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else ChocolateFondant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Lista de productos o estados de carga/error
            when {
                uiState.isLoading && uiState.rawProducts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = BrandyBrown,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                uiState.errorMessage != null && uiState.rawProducts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ChocolateFondant,
                            )
                            OutlinedButton(
                                onClick = { viewModel.loadProducts() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                                border = BorderStroke(1.dp, BrandyBrown),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }

                uiState.filteredProducts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        InventoryEmptyState(onResetFilters = { viewModel.clearFilters() })
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(
                            items = uiState.filteredProducts,
                            key = { it.id },
                        ) { product ->
                            ProductCard(
                                product = product,
                                onEdit = { viewModel.openEditProductDialog(product) },
                                onRegisterMovement = { viewModel.openMovementDialog(product) },
                                onShowKardex = { viewModel.openKardexDialog(product) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogos modales
    if (uiState.isFormDialogOpen) {
        ProductFormDialog(
            isEditing = uiState.editingProduct != null,
            nombre = uiState.formNombre,
            onNombreChange = viewModel::onFormNombreChange,
            precioPesos = uiState.formPrecioPesos,
            onPrecioPesosChange = viewModel::onFormPrecioPesosChange,
            categoria = uiState.formCategoria,
            onCategoriaChange = viewModel::onFormCategoriaChange,
            controlaInventario = uiState.formControlaInventario,
            onControlaInventarioChange = viewModel::onFormControlaInventarioChange,
            stockMinimo = uiState.formStockMinimo,
            onStockMinimoChange = viewModel::onFormStockMinimoChange,
            activo = uiState.formActivo,
            onActivoChange = viewModel::onFormActivoChange,
            isSaving = uiState.isSavingProduct,
            errorMessage = uiState.formErrorMessage,
            existingCategories = uiState.availableCategories,
            onDismiss = { viewModel.closeProductDialog() },
            onSave = { viewModel.saveProduct() },
        )
    }

    if (uiState.isMovementDialogOpen && uiState.movementProduct != null) {
        StockMovementDialog(
            product = uiState.movementProduct!!,
            movementType = uiState.movementType,
            onMovementTypeChange = viewModel::onMovementTypeChange,
            quantity = uiState.movementQuantity,
            onQuantityChange = viewModel::onMovementQuantityChange,
            notas = uiState.movementNotas,
            onNotasChange = viewModel::onMovementNotasChange,
            isAdmin = uiState.isAdmin,
            isSubmitting = uiState.isSubmittingMovement,
            errorMessage = uiState.movementErrorMessage,
            onDismiss = { viewModel.closeMovementDialog() },
            onSubmit = { viewModel.submitStockMovement() },
        )
    }

    if (uiState.isKardexDialogOpen && uiState.kardexProduct != null) {
        ProductKardexDialog(
            product = uiState.kardexProduct!!,
            movements = uiState.kardexMovements,
            isLoading = uiState.isLoadingKardex,
            errorMessage = uiState.kardexErrorMessage,
            onDismiss = { viewModel.closeKardexDialog() },
        )
    }
}

@Composable
private fun InventorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Buscar por nombre o categoría...",
                style = MaterialTheme.typography.bodyMedium,
                color = ChocolateFondant.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = BrandyBrown,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Limpiar búsqueda",
                        tint = BrandyBrown,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = BrandyBrown,
            unfocusedBorderColor = ApricotIllusion,
            focusedTextColor = ChocolateFondant,
            unfocusedTextColor = ChocolateFondant,
        ),
        modifier = modifier,
    )
}

@Composable
private fun InventoryKpiBanner(
    totalCount: Int,
    controlledCount: Int,
    lowStockCount: Int,
    isLowStockFilterActive: Boolean,
    onToggleLowStockFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tarjeta total productos
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = BrandyBrown,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$totalCount Productos",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                    Text(
                        text = "$controlledCount con stock",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = ChocolateFondant.copy(alpha = 0.65f),
                    )
                }
            }
        }

        // Chip interactivo de alerta bajo stock
        val alertColor = if (lowStockCount > 0) OfflineAmber else ChocolateFondant.copy(alpha = 0.4f)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isLowStockFilterActive) OfflineAmber else Color.White.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, if (isLowStockFilterActive) OfflineAmber else ApricotIllusion),
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleLowStockFilter() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (isLowStockFilterActive) Color.White else alertColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (lowStockCount > 0) "$lowStockCount Bajo Stock" else "Stock al día",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStockFilterActive) Color.White else ChocolateFondant,
                    )
                    Text(
                        text = if (isLowStockFilterActive) "Filtro activo" else "Filtrar alertas",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isLowStockFilterActive) Color.White.copy(alpha = 0.9f) else ChocolateFondant.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Chip "Todas"
        val isAllSelected = selectedCategory == null
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isAllSelected) BrandyBrown else Color.White.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, if (isAllSelected) BrandyBrown else ApricotIllusion),
            modifier = Modifier.clickable { onSelectCategory(null) },
        ) {
            Text(
                text = "Todas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isAllSelected) Color.White else ChocolateFondant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Categorías individuales
        categories.forEach { category ->
            val isSelected = selectedCategory.equals(category, ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) BrandyBrown else Color.White.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                modifier = Modifier.clickable { onSelectCategory(category) },
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else ChocolateFondant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onEdit: () -> Unit,
    onRegisterMovement: () -> Unit,
    onShowKardex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Fila superior: Categoría, Estado y Precio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApricotIllusion.copy(alpha = 0.25f),
                            border = BorderStroke(0.5.dp, ApricotIllusion),
                        ) {
                            Text(
                                text = product.categoria.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = BrandyBrown,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        val statusText = if (product.activo) "Activo" else "Inactivo"
                        val statusBg = if (product.activo) SuccessGreen.copy(alpha = 0.12f) else ChocolateFondant.copy(alpha = 0.1f)
                        val statusColor = if (product.activo) SuccessGreen else ChocolateFondant.copy(alpha = 0.7f)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusBg,
                            border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = statusColor,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = product.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        color = ChocolateFondant,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = InventoryUiState.formatPrice(product.precioCentavos),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = BrandyBrown,
                    )
                    Text(
                        text = "Precio unitario",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Control de existencias
            if (!product.controlaInventario) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VanillaCustard.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, ApricotIllusion.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sin control de stock (insumo continuo)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ChocolateFondant.copy(alpha = 0.75f),
                        )
                    }
                }
            } else {
                val stock = product.stockActual ?: 0
                val stockMinimo = product.stockMinimo ?: 0

                val (badgeText, badgeBg, badgeTextColor) = when {
                    stock <= 0 -> Triple("Agotado", ErrorRed.copy(alpha = 0.12f), ErrorRed)
                    stock <= stockMinimo -> Triple("Bajo Stock", OfflineAmber.copy(alpha = 0.15f), OfflineAmber)
                    else -> Triple("Disponible", SuccessGreen.copy(alpha = 0.12f), SuccessGreen)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VanillaCustard.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, ApricotIllusion.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = BrandyBrown,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Existencias: $stock uds (mín. $stockMinimo)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Medium,
                                color = ChocolateFondant,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeBg,
                            border = BorderStroke(0.5.dp, badgeTextColor.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = badgeTextColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Barra de acciones del producto (Editar, Movimiento de Stock, Kardex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Editar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (product.controlaInventario) {
                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onRegisterMovement,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BrandyBrown.copy(alpha = 0.08f),
                            contentColor = BrandyBrown,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Stock",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onShowKardex,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChocolateFondant),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Kardex",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryEmptyState(
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = ApricotIllusion.copy(alpha = 0.2f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.FilterAltOff,
                    contentDescription = null,
                    tint = BrandyBrown.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "No se encontraron productos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Ningún producto coincide con los filtros o la búsqueda actual.",
            style = MaterialTheme.typography.bodySmall,
            color = ChocolateFondant.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onResetFilters,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BrandyBrown),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BrandyBrown,
            ),
        ) {
            Text(
                text = "Limpiar filtros",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// --- Diálogo de Creación / Edición de Producto ---

@Composable
private fun ProductFormDialog(
    isEditing: Boolean,
    nombre: String,
    onNombreChange: (String) -> Unit,
    precioPesos: String,
    onPrecioPesosChange: (String) -> Unit,
    categoria: String,
    onCategoriaChange: (String) -> Unit,
    controlaInventario: Boolean,
    onControlaInventarioChange: (Boolean) -> Unit,
    stockMinimo: String,
    onStockMinimoChange: (String) -> Unit,
    activo: Boolean,
    onActivoChange: (Boolean) -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (isEditing) "Editar Producto" else "Nuevo Producto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
                Text(
                    text = if (isEditing) "Actualiza la información del menú" else "Registra un producto en el menú",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(16.dp))

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ErrorRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Nombre
                OutlinedTextField(
                    value = nombre,
                    onValueChange = onNombreChange,
                    label = { Text("Nombre del Producto") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // Categoría
                OutlinedTextField(
                    value = categoria,
                    onValueChange = onCategoriaChange,
                    label = { Text("Categoría (ej. Tacos, Bebidas)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Sugerencias de categorías existentes
                if (existingCategories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        existingCategories.take(6).forEach { cat ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ApricotIllusion.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, ApricotIllusion),
                                modifier = Modifier.clickable { onCategoriaChange(cat) },
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = ChocolateFondant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Precio en Pesos
                OutlinedTextField(
                    value = precioPesos,
                    onValueChange = onPrecioPesosChange,
                    label = { Text("Precio en Pesos MXN (ej. 35.00)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))

                // Toggle Controla Inventario
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Controlar existencias",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = if (controlaInventario) "Lleva conteo de stock físico" else "Insumo continuo sin límite",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ChocolateFondant.copy(alpha = 0.65f),
                        )
                    }
                    Switch(
                        checked = controlaInventario,
                        onCheckedChange = onControlaInventarioChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandyBrown,
                            uncheckedThumbColor = ApricotIllusion,
                            uncheckedTrackColor = VanillaCustard,
                        ),
                    )
                }

                if (controlaInventario) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = stockMinimo,
                        onValueChange = onStockMinimoChange,
                        label = { Text("Stock Mínimo para Alerta") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandyBrown,
                            unfocusedBorderColor = ApricotIllusion,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (isEditing) {
                    Spacer(Modifier.height(14.dp))
                    // Toggle Activo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Producto Activo en Menú",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = ChocolateFondant,
                            )
                            Text(
                                text = if (activo) "Visible para venta y órdenes" else "Oculto/desactivado del catálogo",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ChocolateFondant.copy(alpha = 0.65f),
                            )
                        }
                        Switch(
                            checked = activo,
                            onCheckedChange = onActivoChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandyBrown,
                                uncheckedThumbColor = ApricotIllusion,
                                uncheckedTrackColor = VanillaCustard,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Cancelar", color = ChocolateFondant)
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedButton(
                        onClick = onSave,
                        enabled = !isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, BrandyBrown),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isEditing) "Actualizar" else "Guardar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// --- Diálogo de Registro de Movimiento de Stock ---

@Composable
private fun StockMovementDialog(
    product: Product,
    movementType: InventoryMovementType,
    onMovementTypeChange: (InventoryMovementType) -> Unit,
    quantity: String,
    onQuantityChange: (String) -> Unit,
    notas: String,
    onNotasChange: (String) -> Unit,
    isAdmin: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Movimiento de Stock",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
                Text(
                    text = product.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ChocolateFondant,
                )

                Spacer(Modifier.height(8.dp))

                // Info de stock actual
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VanillaCustard.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, ApricotIllusion),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Stock Actual: ${product.stockActual ?: 0} uds",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Mínimo: ${product.stockMinimo ?: 0} uds",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ErrorRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = "Tipo de Movimiento",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )

                Spacer(Modifier.height(6.dp))

                // Selector de tipo de movimiento (Entrada, Merma, Ajuste)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val types = mutableListOf(
                        InventoryMovementType.ENTRADA to "Entrada (+)",
                        InventoryMovementType.MERMA to "Merma (-)",
                    )
                    if (isAdmin) {
                        types.add(InventoryMovementType.AJUSTE to "Ajuste")
                    }

                    types.forEach { (type, label) ->
                        val isSelected = movementType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) BrandyBrown else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onMovementTypeChange(type) },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else ChocolateFondant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Cantidad
                val qtyLabel = when (movementType) {
                    InventoryMovementType.ENTRADA -> "Cantidad a ingresar (+)"
                    InventoryMovementType.MERMA -> "Cantidad a descontar (-)"
                    InventoryMovementType.AJUSTE -> "Delta de ajuste (+/- unidades)"
                    else -> "Cantidad"
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    label = { Text(qtyLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // Notas / Motivo
                OutlinedTextField(
                    value = notas,
                    onValueChange = onNotasChange,
                    label = { Text("Motivo / Notas del movimiento") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Cancelar", color = ChocolateFondant)
                    }

                    Spacer(Modifier.width(10.dp))

                    OutlinedButton(
                        onClick = onSubmit,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, BrandyBrown),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Diálogo de Historial de Kardex ---

@Composable
private fun ProductKardexDialog(
    product: Product,
    movements: List<StockMovement>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Historial Kardex",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                        Text(
                            text = product.nombre,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = BrandyBrown,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    errorMessage != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                            )
                        }
                    }

                    movements.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Sin movimientos confirmados en el kardex.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant.copy(alpha = 0.65f),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(movements, key = { it.id }) { movement ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = VanillaCustard.copy(alpha = 0.4f),
                                    border = BorderStroke(0.5.dp, ApricotIllusion),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                val (typeColor, typeBg) = when (movement.type) {
                                                    InventoryMovementType.ENTRADA -> SuccessGreen to SuccessGreen.copy(alpha = 0.12f)
                                                    InventoryMovementType.MERMA -> ErrorRed to ErrorRed.copy(alpha = 0.12f)
                                                    InventoryMovementType.AJUSTE -> OfflineAmber to OfflineAmber.copy(alpha = 0.12f)
                                                    InventoryMovementType.VENTA -> BrandyBrown to BrandyBrown.copy(alpha = 0.12f)
                                                    else -> ChocolateFondant to ChocolateFondant.copy(alpha = 0.12f)
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = typeBg,
                                                ) {
                                                    Text(
                                                        text = movement.type.databaseValue.uppercase(),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = typeColor,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }

                                                Text(
                                                    text = movement.createdAt.take(10),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = ChocolateFondant.copy(alpha = 0.6f),
                                                )
                                            }

                                            movement.notes?.let { note ->
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = note,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = ChocolateFondant.copy(alpha = 0.85f),
                                                )
                                            }
                                        }

                                        val qtyPrefix = if (movement.quantity > 0) "+" else ""
                                        Text(
                                            text = "$qtyPrefix${movement.quantity}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = if (movement.quantity > 0) SuccessGreen else if (movement.quantity < 0) ErrorRed else ChocolateFondant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Cerrar", color = ChocolateFondant)
                    }
                }
            }
        }
    }
}
