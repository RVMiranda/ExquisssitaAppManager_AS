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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
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

            // Header con título y botón de refrescar
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
                        color = ChocolateFondant.copy(alpha = 0.75f),
                    )
                }

                IconButton(
                    onClick = { viewModel.loadProducts(isRefresh = true) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Actualizar catálogo",
                        tint = BrandyBrown,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Barra de búsqueda en vivo
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
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
                        contentDescription = "Buscar",
                        tint = BrandyBrown,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Limpiar búsqueda",
                                tint = BrandyBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.8f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                    focusedBorderColor = BrandyBrown,
                    unfocusedBorderColor = ApricotIllusion,
                    focusedTextColor = ChocolateFondant,
                    unfocusedTextColor = ChocolateFondant,
                ),
            )

            Spacer(Modifier.height(10.dp))

            // Banner resumen de métricas (KPIs de catálogo)
            InventoryKpiBar(
                totalCount = uiState.rawProducts.count { it.activo },
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

            // Selector horizontal de Categorías
            if (uiState.availableCategories.isNotEmpty()) {
                CategoryChipRow(
                    categories = uiState.availableCategories,
                    selectedCategory = uiState.selectedCategory,
                    onSelectCategory = viewModel::onCategorySelect,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Chips de estado (Activos / Todos / Inactivos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProductStatusFilter.entries.forEach { filter ->
                    val isSelected = uiState.statusFilter == filter
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) BrandyBrown else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.onStatusFilterSelect(filter) },
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else ChocolateFondant,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Contenido principal
            when {
                uiState.isLoading && uiState.rawProducts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandyBrown)
                    }
                }
                uiState.filteredProducts.isEmpty() -> {
                    InventoryEmptyState(
                        hasFilters = uiState.searchQuery.isNotBlank() ||
                            uiState.selectedCategory != null ||
                            uiState.statusFilter != ProductStatusFilter.ACTIVOS ||
                            uiState.stockFilter != StockFilter.TODOS,
                        onClearFilters = viewModel::clearFilters,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.filteredProducts, key = { it.id }) { product ->
                            ProductCard(product = product)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryKpiBar(
    totalCount: Int,
    controlledCount: Int,
    lowStockCount: Int,
    isLowStockFilterActive: Boolean,
    onToggleLowStockFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Total productos activos
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
            // Fila superior: Categoría, Estado y Nombre
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
                        // Badge de Categoría
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

                        // Badge de estado Activo/Inactivo
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
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Precio tabulado
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = InventoryUiState.formatPrice(product.precioCentavos),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Default,
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

            // Fila inferior: Estado de inventario y existencias
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = VanillaCustard.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, ApricotIllusion.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (product.controlaInventario) {
                        val current = product.stockActual ?: 0
                        val min = product.stockMinimo ?: 0
                        val isOut = current <= 0
                        val isLow = current in 1..min

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when {
                                            isOut -> BrandyBrown
                                            isLow -> OfflineAmber
                                            else -> SuccessGreen
                                        },
                                        shape = CircleShape,
                                    ),
                            )

                            Text(
                                text = when {
                                    isOut -> "Agotado"
                                    isLow -> "Stock Bajo"
                                    else -> "Disponible"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isOut -> BrandyBrown
                                    isLow -> OfflineAmber
                                    else -> SuccessGreen
                                },
                            )
                        }

                        Text(
                            text = "Stock: $current uds (mín. $min)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = ChocolateFondant.copy(alpha = 0.75f),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Sin control de stock (insumo continuo)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ChocolateFondant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryEmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterAltOff,
                contentDescription = null,
                tint = ApricotIllusion,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No se encontraron productos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasFilters) {
                    "Ningún producto coincide con los filtros o la búsqueda actual."
                } else {
                    "El catálogo no tiene productos registrados en la base de datos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (hasFilters) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandyBrown),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandyBrown,
                    ),
                ) {
                    Text(text = "Limpiar filtros", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
