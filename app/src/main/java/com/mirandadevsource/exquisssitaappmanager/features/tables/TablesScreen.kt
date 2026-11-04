package com.mirandadevsource.exquisssitaappmanager.features.tables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.core.utils.DateUtils
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

private val AvailableGreen = Color(0xFF2E7D32)
private val AvailableGreenBg = Color(0xFFE8F5E9)
private val AvailableGreenBorder = Color(0xFFA5D6A7)

@Composable
fun TablesScreen(
    viewModel: TablesViewModel,
    onOpenOrderForTable: ((tableId: String) -> Unit)? = null,
    onViewOrder: ((orderId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Encabezado
            TablesHeader(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshTables() },
                onNewTable = { viewModel.openCreateTableDialog() },
            )

            Spacer(Modifier.height(10.dp))

            // Banner de Mensaje de Usuario (Éxito)
            AnimatedVisibility(visible = uiState.userMessage != null) {
                uiState.userMessage?.let { msg ->
                    UserMessageBanner(
                        message = msg,
                        isError = false,
                        onDismiss = { viewModel.dismissUserMessage() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Banner de Error
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { err ->
                    UserMessageBanner(
                        message = err,
                        isError = true,
                        onDismiss = { viewModel.dismissErrorMessage() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Banner de KPIs del Salón
            TablesKpiBanner(
                total = uiState.totalCount,
                libres = uiState.freeCount,
                ocupadas = uiState.occupiedCount,
                reservadas = uiState.reservedCount,
            )

            Spacer(Modifier.height(10.dp))

            // Barra de Búsqueda
            TableSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
            )

            Spacer(Modifier.height(8.dp))

            // Selector de Filtros por Estado
            TableStatusFilterRow(
                currentFilter = uiState.statusFilter,
                onFilterSelected = viewModel::onStatusFilterChange,
            )

            Spacer(Modifier.height(12.dp))

            // Contenido Principal (Cuadrícula o Estados de Carga / Vacío)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    uiState.isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = BrandyBrown,
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Cargando mesas del salón...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ChocolateFondant,
                            )
                        }
                    }

                    uiState.filteredTables.isEmpty() -> {
                        TablesEmptyState(
                            hasFilter = uiState.searchQuery.isNotBlank() || uiState.statusFilter != TableStatusFilter.TODAS,
                            onClearFilters = viewModel::clearFilters,
                            onNewTable = viewModel::openCreateTableDialog,
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(
                                items = uiState.filteredTables,
                                key = { it.id },
                            ) { table ->
                                TableCard(
                                    table = table,
                                    onClick = { viewModel.onTableClick(table) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo "Nueva Mesa"
    if (uiState.isCreateTableDialogOpen) {
        CreateTableDialog(
            tableNumber = uiState.newTableNumber,
            onTableNumberChange = viewModel::onNewTableNumberChange,
            isSaving = uiState.isSavingTable,
            errorMessage = uiState.createTableError,
            onDismiss = viewModel::dismissCreateTableDialog,
            onSave = viewModel::createTable,
        )
    }

    // Diálogo "Detalle de Mesa"
    if (uiState.isTableDetailDialogOpen && uiState.selectedTable != null) {
        TableDetailDialog(
            table = uiState.selectedTable!!,
            orders = uiState.tableOrders,
            isLoadingOrders = uiState.isLoadingOrders,
            isTogglingReservation = uiState.isTogglingReservation,
            actionError = uiState.detailActionError,
            onToggleReservation = { viewModel.toggleReservation(uiState.selectedTable!!) },
            onOpenOrder = onOpenOrderForTable,
            onViewOrder = onViewOrder,
            onDismiss = viewModel::dismissTableDetailDialog,
        )
    }
}

@Composable
private fun TablesHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNewTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        label = "refresh_rotation",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mesas y Salón",
                style = MaterialTheme.typography.headlineSmall,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Control de mesas, ocupación del salón y reservas",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Recargar mesas",
                    tint = BrandyBrown,
                    modifier = Modifier.rotate(rotation),
                )
            }

            Button(
                onClick = onNewTable,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Nueva",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun TablesKpiBanner(
    total: Int,
    libres: Int,
    ocupadas: Int,
    reservadas: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpiCard(
            title = "Total Mesas",
            value = total.toString(),
            icon = Icons.Outlined.TableRestaurant,
            color = ChocolateFondant,
        )
        KpiCard(
            title = "Libres",
            value = libres.toString(),
            icon = Icons.Outlined.CheckCircleOutline,
            color = AvailableGreen,
        )
        KpiCard(
            title = "Ocupadas",
            value = ocupadas.toString(),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            color = BrandyBrown,
        )
        KpiCard(
            title = "Reservadas",
            value = reservadas.toString(),
            icon = Icons.Outlined.BookmarkBorder,
            color = OfflineAmber,
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun TableSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Buscar por número de mesa...",
                color = ChocolateFondant.copy(alpha = 0.5f),
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = BrandyBrown,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
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
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandyBrown,
            unfocusedBorderColor = ApricotIllusion,
            focusedTextColor = ChocolateFondant,
            unfocusedTextColor = ChocolateFondant,
            cursorColor = BrandyBrown,
        ),
    )
}

@Composable
private fun TableStatusFilterRow(
    currentFilter: TableStatusFilter,
    onFilterSelected: (TableStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TableStatusFilter.entries.forEach { filter ->
            val isSelected = currentFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) BrandyBrown else ApricotIllusion,
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = BrandyBrown,
                    labelColor = ChocolateFondant,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun TableCard(
    table: Table,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (statusLabel, statusBg, statusBorder, statusTextColor) = when (table.status) {
        TableStatus.FREE -> TableStatusVisual(
            label = "Libre",
            bg = AvailableGreenBg,
            border = AvailableGreenBorder,
            textColor = AvailableGreen,
        )
        TableStatus.OCCUPIED -> TableStatusVisual(
            label = if (table.openOrderCount == 1) "1 comanda" else "${table.openOrderCount} comandas",
            bg = ApricotIllusion.copy(alpha = 0.35f),
            border = BrandyBrown,
            textColor = BrandyBrown,
        )
        TableStatus.RESERVED -> TableStatusVisual(
            label = "Reservada",
            bg = OfflineAmber.copy(alpha = 0.15f),
            border = OfflineAmber,
            textColor = OfflineAmber,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fila superior: Ícono y badge de sincronización local
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VanillaCustard,
                    border = BorderStroke(0.5.dp, ApricotIllusion),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TableRestaurant,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(22.dp),
                    )
                }

                if (table.isPendingSync) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OfflineAmber.copy(alpha = 0.15f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "Pendiente de sincronizar",
                                tint = OfflineAmber,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "Local",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OfflineAmber,
                            )
                        }
                    }
                }
            }

            // Número o nombre de mesa
            Text(
                text = table.number,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Pastilla de estado
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusBg,
                border = BorderStroke(0.5.dp, statusBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .then(
                                Modifier.clickable(enabled = false) {}
                            )
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor,
                    )
                }
            }
        }
    }
}

private data class TableStatusVisual(
    val label: String,
    val bg: Color,
    val border: Color,
    val textColor: Color,
)

@Composable
private fun TablesEmptyState(
    hasFilter: Boolean,
    onClearFilters: () -> Unit,
    onNewTable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.TableRestaurant,
            contentDescription = null,
            tint = BrandyBrown.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasFilter) "No hay mesas que coincidan con la búsqueda" else "No hay mesas registradas en el salón",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasFilter) "Intenta limpiar los filtros o buscar con otro término." else "Registra las mesas físicas del restaurante para comenzar a abrir comandas.",
            style = MaterialTheme.typography.bodySmall,
            color = ChocolateFondant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        if (hasFilter) {
            OutlinedButton(
                onClick = onClearFilters,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BrandyBrown),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
            ) {
                Text("Limpiar Filtros")
            }
        } else {
            Button(
                onClick = onNewTable,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Registrar Primera Mesa")
            }
        }
    }
}

@Composable
private fun CreateTableDialog(
    tableNumber: String,
    onTableNumberChange: (String) -> Unit,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "Registrar Nueva Mesa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Text(
                    text = "Ingresa el número o nombre identificador de la mesa física.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(14.dp))

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(0.5.dp, Color(0xFFE57373)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFC62828),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = tableNumber,
                    onValueChange = onTableNumberChange,
                    label = { Text("Número o Nombre (ej. Mesa 5, Barra 2)") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                        cursorColor = BrandyBrown,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                    ) {
                        Text("Cancelar", color = ChocolateFondant)
                    }

                    Spacer(Modifier.width(10.dp))

                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Guardar Mesa")
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDetailDialog(
    table: Table,
    orders: List<Order>,
    isLoadingOrders: Boolean,
    isTogglingReservation: Boolean,
    actionError: String?,
    onToggleReservation: () -> Unit,
    onOpenOrder: ((String) -> Unit)? = null,
    onViewOrder: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = table.number,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Estado actual: ${table.status.databaseValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Error de Acción si existe
                if (actionError != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(0.5.dp, Color(0xFFE57373)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = actionError,
                            color = Color(0xFFC62828),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Gestión de Reservación
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VanillaCustard,
                    border = BorderStroke(0.5.dp, ApricotIllusion),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reservación",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChocolateFondant,
                            )
                            Text(
                                text = if (table.status == TableStatus.RESERVED) "Mesa apartada para reservación"
                                else if (table.status == TableStatus.OCCUPIED) "No disponible (mesa con órdenes abiertas)"
                                else "Mesa sin apartar",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChocolateFondant.copy(alpha = 0.7f),
                            )
                        }

                        if (table.status != TableStatus.OCCUPIED) {
                            OutlinedButton(
                                onClick = onToggleReservation,
                                enabled = !isTogglingReservation,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (table.status == TableStatus.RESERVED) BrandyBrown else OfflineAmber,
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (table.status == TableStatus.RESERVED) BrandyBrown else OfflineAmber,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                if (isTogglingReservation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandyBrown,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (table.status == TableStatus.RESERVED) "Liberar" else "Reservar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Sección Comandas Abiertas
                Text(
                    text = "Comandas en esta mesa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )

                Spacer(Modifier.height(8.dp))

                when {
                    isLoadingOrders -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = BrandyBrown,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Consultando comandas...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant,
                            )
                        }
                    }

                    orders.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AvailableGreenBg.copy(alpha = 0.5f),
                            border = BorderStroke(0.5.dp, AvailableGreenBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = AvailableGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Esta mesa no tiene comandas activas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AvailableGreen,
                                )
                            }
                        }
                    }

                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            orders.forEach { order ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VanillaCustard,
                                    border = BorderStroke(0.5.dp, ApricotIllusion),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = onViewOrder != null) {
                                            onDismiss()
                                            onViewOrder?.invoke(order.id)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Comanda #${order.id.take(8).uppercase()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ChocolateFondant,
                                            )
                                            Text(
                                                text = "Apertura: ${DateUtils.formatOrderDateTime(order.openedAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ChocolateFondant.copy(alpha = 0.7f),
                                            )
                                            if (!order.notes.isNullOrBlank()) {
                                                Text(
                                                    text = "Nota: ${order.notes}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BrandyBrown,
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = BrandyBrown,
                                        ) {
                                            Text(
                                                text = order.status.databaseValue,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onOpenOrder != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenOrder(table.id)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandyBrown,
                                contentColor = Color.White,
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp),
                        ) {
                            Text("Abrir Comanda")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        Text("Listo")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val borderColor = if (isError) Color(0xFFE57373) else Color(0xFF81C784)
    val textColor = if (isError) Color(0xFFC62828) else Color(0xFF2E7D32)
    val icon = if (isError) Icons.Outlined.Info else Icons.Outlined.CheckCircleOutline

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cerrar",
                    tint = textColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
