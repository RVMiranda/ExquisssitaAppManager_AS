package com.mirandadevsource.exquisssitaappmanager.features.orders

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.mirandadevsource.exquisssitaappmanager.core.utils.DateUtils
import com.mirandadevsource.exquisssitaappmanager.features.payments.CheckoutScreen
import com.mirandadevsource.exquisssitaappmanager.features.payments.PaymentViewModel
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.entity.TableStatus
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OrdersScreen(
    viewModel: OrderViewModel,
    paymentViewModel: PaymentViewModel = hiltViewModel(),
    onNavigateToTables: (() -> Unit)? = null,
    onNavigateToCheckout: ((Order) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val paymentUiState by paymentViewModel.uiState.collectAsState()

    // Si el usuario abrió el flujo de Cobro, mostramos la pantalla de Checkout
    if (uiState.activeCheckoutOrder != null) {
        CheckoutScreen(
            uiState = paymentUiState,
            onBack = {
                viewModel.dismissCheckout()
                paymentViewModel.dismissUserMessage()
            },
            onSplitModeSelected = paymentViewModel::onSplitModeSelected,
            onPaymentMethodSelected = paymentViewModel::onPaymentMethodSelected,
            onItemQuantityChanged = paymentViewModel::onItemQuantityChanged,
            onSelectAllItems = paymentViewModel::onSelectAllItems,
            onClearItemSelection = paymentViewModel::onClearItemSelection,
            onSplitCountChanged = paymentViewModel::onSplitCountChanged,
            onCashReceivedChanged = paymentViewModel::onCashReceivedInputChanged,
            onDismissMessage = paymentViewModel::dismissUserMessage,
            onSubmitPayment = {
                paymentViewModel.processPayment()
            },
            modifier = modifier,
        )
    } else if (uiState.selectedOrderId != null) {
        // Si hay una orden seleccionada para gestionar, renderizamos la vista de Detalle de Comanda
        OrderDetailScreen(
            order = uiState.selectedOrder,
            details = uiState.selectedOrderDetails,
            payments = uiState.selectedOrderPayments,
            table = uiState.selectedOrder?.tableId?.let { uiState.tableMap[it] },
            productMap = uiState.productMap,
            isLoadingDetails = uiState.isLoadingOrderDetails,
            userMessage = uiState.orderActionError ?: uiState.successMessage,
            isErrorMessage = uiState.orderActionError != null,
            onDismissMessage = viewModel::dismissUserMessage,
            onBack = viewModel::dismissOrderDetail,
            onOpenAddProduct = viewModel::openAddItemDialog,
            onUpdateQuantity = viewModel::updateItemQuantity,
            onPromptCancelItem = viewModel::promptCancelItem,
            onPromptCancelOrder = viewModel::promptCancelOrder,
            onNavigateToCheckout = { orderToPay ->
                viewModel.startCheckout(orderToPay)
                paymentViewModel.loadOrder(orderToPay)
                onNavigateToCheckout?.invoke(orderToPay)
            },
            modifier = modifier,
        )
    } else {
        // Vista Principal: Lista de Comandas y KPIs
        Surface(
            modifier = modifier.fillMaxSize(),
            color = VanillaCustard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Cabecera Principal
                OrdersHeader(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onNewOrder = { viewModel.openNewOrderDialog() },
                )

                Spacer(Modifier.height(8.dp))

                // Mensajes de Alerta / Éxito
                val bannerMsg = uiState.errorMessage ?: uiState.successMessage
                if (bannerMsg != null) {
                    UserMessageBanner(
                        message = bannerMsg,
                        isError = uiState.errorMessage != null,
                        onDismiss = viewModel::dismissUserMessage,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Selector de Ámbito: Comandas de Hoy vs Historial
                OrdersScopeSwitcher(
                    selectedTab = uiState.scopeTab ?: OrderScopeTab.DEL_DIA,
                    todayCount = uiState.todayOrders.size,
                    historyCount = uiState.historyOrders.size,
                    onTabSelected = viewModel::onScopeTabChange,
                )

                Spacer(Modifier.height(10.dp))

                // Banner de Métricas Operativas (KPIs)
                OrdersKpiBanner(
                    totalOrders = if (uiState.scopeTab == OrderScopeTab.HISTORIAL) uiState.historyOrders.size else uiState.todayOrders.size,
                    openOrders = uiState.openOrdersCount,
                    openTableOrders = uiState.openTableOrdersCount,
                    openTakeoutOrders = uiState.openTakeoutOrdersCount,
                )

                Spacer(Modifier.height(12.dp))

                // Barra de Búsqueda y Filtros
                OrdersSearchAndFilters(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    statusFilter = uiState.statusFilter,
                    onStatusFilterChange = viewModel::onStatusFilterChange,
                    tableFilterId = uiState.tableFilterId,
                    tableMap = uiState.tableMap,
                    onClearTableFilter = { viewModel.filterByTable(null) },
                )

                Spacer(Modifier.height(12.dp))

                // Lista de Comandas
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandyBrown, strokeWidth = 2.dp)
                    }
                } else if (uiState.filteredOrders.isEmpty()) {
                    OrdersEmptyState(
                        hasFilter = uiState.searchQuery.isNotBlank() || uiState.statusFilter != OrderFilter.TODAS || uiState.tableFilterId != null,
                        onNewOrder = { viewModel.openNewOrderDialog() },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.filteredOrders, key = { it.id }) { order ->
                            OrderCard(
                                order = order,
                                table = order.tableId?.let { uiState.tableMap[it] },
                                onClick = { viewModel.selectOrder(order.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Abrir Nueva Comanda
    if (uiState.isOpenOrderDialogOpen) {
        OpenOrderDialog(
            serviceType = uiState.newOrderServiceType,
            tables = uiState.tables,
            selectedTableId = uiState.newOrderTableId,
            notes = uiState.newOrderNotes,
            isSubmitting = uiState.isSubmittingOrder,
            errorMessage = uiState.openOrderError,
            onServiceTypeChange = viewModel::onNewOrderServiceTypeChange,
            onTableSelect = viewModel::onNewOrderTableSelect,
            onNotesChange = viewModel::onNewOrderNotesChange,
            onDismiss = viewModel::dismissNewOrderDialog,
            onConfirm = viewModel::createOrder,
        )
    }

    // Modal: Agregar Producto
    if (uiState.isAddItemDialogOpen) {
        AddProductDialog(
            products = uiState.filteredProducts,
            categories = uiState.availableCategories,
            selectedCategory = uiState.selectedCategory,
            searchQuery = uiState.productSearchQuery,
            selectedProduct = uiState.selectedProduct,
            quantity = uiState.itemQuantity,
            customPricePesos = uiState.itemCustomPricePesos,
            notes = uiState.itemNotes,
            isSubmitting = uiState.isSubmittingItem,
            errorMessage = uiState.addItemError,
            onCategorySelect = viewModel::onCategorySelect,
            onSearchQueryChange = viewModel::onProductSearchQueryChange,
            onProductSelect = viewModel::onProductSelect,
            onQuantityChange = viewModel::onQuantityChange,
            onCustomPriceChange = viewModel::onCustomPriceChange,
            onNotesChange = viewModel::onItemNotesChange,
            onDismiss = viewModel::dismissAddItemDialog,
            onConfirm = viewModel::addItemToOrder,
        )
    }

    // Modal: Confirmación de Cancelación
    if (uiState.itemToCancel != null || uiState.orderToCancel != null) {
        ConfirmCancelDialog(
            isOrderCancellation = uiState.orderToCancel != null,
            isCancelling = uiState.isCancellingAction,
            onDismiss = viewModel::dismissCancelDialog,
            onConfirm = {
                if (uiState.orderToCancel != null) {
                    viewModel.confirmCancelOrder()
                } else {
                    viewModel.confirmCancelItem()
                }
            },
        )
    }
}

@Composable
private fun OrdersHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNewOrder: () -> Unit,
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
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "Órdenes y Comandas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Text(
                text = "Gestión de comandas y pedidos",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refrescar",
                    tint = BrandyBrown,
                    modifier = Modifier.rotate(rotation),
                )
            }

            Spacer(Modifier.width(4.dp))

            Button(
                onClick = onNewOrder,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Nueva",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun OrdersKpiBanner(
    totalOrders: Int,
    openOrders: Int,
    openTableOrders: Int,
    openTakeoutOrders: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpiChip(
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            count = totalOrders,
            label = "Total Órdenes",
            color = BrandyBrown,
        )
        KpiChip(
            icon = Icons.Outlined.CheckCircle,
            count = openOrders,
            label = "Abiertas",
            color = Color(0xFF2E7D32),
        )
        KpiChip(
            icon = Icons.Outlined.TableRestaurant,
            count = openTableOrders,
            label = "En Mesas",
            color = BrandyBrown,
        )
        KpiChip(
            icon = Icons.Outlined.TakeoutDining,
            count = openTakeoutOrders,
            label = "Para Llevar",
            color = OfflineAmber,
        )
    }
}

@Composable
private fun KpiChip(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    lineHeight = 16.sp,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun OrdersSearchAndFilters(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    statusFilter: OrderFilter,
    onStatusFilterChange: (OrderFilter) -> Unit,
    tableFilterId: String?,
    tableMap: Map<String, Table>,
    onClearTableFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Campo de Búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar por folio, mesa o notas...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Buscar",
                    tint = BrandyBrown,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Limpiar",
                            tint = BrandyBrown,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = BrandyBrown,
                unfocusedBorderColor = ApricotIllusion,
                focusedTextColor = ChocolateFondant,
                unfocusedTextColor = ChocolateFondant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // Chips de Filtro
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tableFilterId != null) {
                val tableNum = tableMap[tableFilterId]?.number ?: "Mesa"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandyBrown.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, BrandyBrown),
                    modifier = Modifier.clickable(onClick = onClearTableFilter),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mesa: $tableNum",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Quitar filtro de mesa",
                            tint = BrandyBrown,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            OrderFilter.entries.forEach { filter ->
                val isSelected = statusFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusFilterChange(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        labelColor = ChocolateFondant,
                        selectedContainerColor = BrandyBrown,
                        selectedLabelColor = Color.White,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) BrandyBrown else ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    table: Table?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Encabezado de la Tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (order.serviceType) {
                            ServiceType.TABLE -> Icons.Outlined.TableRestaurant
                            ServiceType.TAKEOUT -> Icons.Outlined.TakeoutDining
                            ServiceType.DELIVERY -> Icons.Outlined.DeliveryDining
                        },
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (order.serviceType) {
                            ServiceType.TABLE -> "Mesa ${table?.number ?: "Asignada"}"
                            ServiceType.TAKEOUT -> "Para Llevar"
                            ServiceType.DELIVERY -> "Domicilio"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                // Badge de Estatus de Orden
                val (statusBg, statusFg) = when (order.status) {
                    OrderStatus.OPEN -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
                    OrderStatus.CLOSED -> BrandyBrown.copy(alpha = 0.15f) to BrandyBrown
                    OrderStatus.CANCELLED -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusFg.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = order.status.databaseValue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Subtítulo con folio y hora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Folio: #${order.id.take(8).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = if (DateUtils.isTodayInMexico(order.openedAt)) {
                        DateUtils.formatOrderTime(order.openedAt)
                    } else {
                        DateUtils.formatOrderDateTime(order.openedAt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.6f),
                )
            }

            if (!order.notes.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Nota: ${order.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (order.isPendingSync) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = OfflineAmber,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Pendiente de sincronizar",
                        style = MaterialTheme.typography.labelSmall,
                        color = OfflineAmber,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            // Botón de Gestión
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Gestionar comanda ->",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
            }
        }
    }
}

@Composable
private fun OrdersEmptyState(
    hasFilter: Boolean,
    onNewOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, ApricotIllusion),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = BrandyBrown.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasFilter) "Sin comandas coincidentes" else "No hay comandas registradas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasFilter) "Intenta modificar el término de búsqueda o restablecer los filtros." else "Abre una nueva comanda para comenzar a tomar pedidos.",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            if (!hasFilter) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onNewOrder,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                ) {
                    Text("Abrir Nueva Comanda")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenOrderDialog(
    serviceType: ServiceType,
    tables: List<Table>,
    selectedTableId: String?,
    notes: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onServiceTypeChange: (ServiceType) -> Unit,
    onTableSelect: (String?) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var isTableDropdownExpanded by remember { mutableStateOf(false) }

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
                    text = "Abrir Nueva Comanda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
                Text(
                    text = "Selecciona el tipo de servicio y los detalles de apertura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(16.dp))

                // Selector de Tipo de Servicio
                Text(
                    text = "Tipo de Servicio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ServiceType.entries.forEach { type ->
                        val isSelected = serviceType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { onServiceTypeChange(type) },
                            label = {
                                Text(
                                    text = type.databaseValue,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) BrandyBrown else ApricotIllusion,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Selector de Mesa si es servicio en comedor
                if (serviceType == ServiceType.TABLE) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Mesa Física",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                    Spacer(Modifier.height(6.dp))

                    val selectedTable = tables.firstOrNull { it.id == selectedTableId }
                    ExposedDropdownMenuBox(
                        expanded = isTableDropdownExpanded,
                        onExpandedChange = { isTableDropdownExpanded = !isTableDropdownExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedTable?.let { "Mesa ${it.number} (${it.status.databaseValue})" } ?: "Seleccionar mesa...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTableDropdownExpanded) },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandyBrown,
                                unfocusedBorderColor = ApricotIllusion,
                                focusedTextColor = ChocolateFondant,
                                unfocusedTextColor = ChocolateFondant,
                            ),
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )

                        ExposedDropdownMenu(
                            expanded = isTableDropdownExpanded,
                            onDismissRequest = { isTableDropdownExpanded = false },
                            containerColor = Color.White,
                        ) {
                            tables.forEach { table ->
                                val statusLabel = when (table.status) {
                                    TableStatus.FREE -> "Libre"
                                    TableStatus.OCCUPIED -> "Ocupada"
                                    TableStatus.RESERVED -> "Reservada"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text("Mesa ${table.number}", fontWeight = FontWeight.Medium)
                                            Text(
                                                text = statusLabel,
                                                fontSize = 12.sp,
                                                color = if (table.status == TableStatus.FREE) Color(0xFF2E7D32) else OfflineAmber,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onTableSelect(table.id)
                                        isTableDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Notas opcionales
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notas de comanda (opcional)") },
                    placeholder = { Text("Ej. Cliente frecuente, terraza...") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChocolateFondant,
                        ),
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Abrir Comanda")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProductDialog(
    products: List<Product>,
    categories: List<String>,
    selectedCategory: String?,
    searchQuery: String,
    selectedProduct: Product?,
    quantity: Int,
    customPricePesos: String,
    notes: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCategorySelect: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onProductSelect: (Product) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onCustomPriceChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ApricotIllusion),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
            ) {
                Text(
                    text = "Agregar Producto a Comanda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
                Text(
                    text = "Selecciona del menú y especifica cantidad y notas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(10.dp))

                // Buscador de Producto
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar producto...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = BrandyBrown,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Chips de Categorías
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("Todas", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandyBrown,
                            selectedLabelColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(6.dp),
                    )
                    categories.forEach { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelect(if (isSelected) null else cat) },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, ApricotIllusion),
                            shape = RoundedCornerShape(6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Lista de Productos del Catálogo
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (products.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No se encontraron productos en esta categoría.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChocolateFondant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                        items(products, key = { it.id }) { product ->
                            val isSelected = selectedProduct?.id == product.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) BrandyBrown.copy(alpha = 0.1f) else Color(0xFFFAFAFA),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BrandyBrown else Color(0xFFEEEEEE),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onProductSelect(product) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.nombre,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = ChocolateFondant,
                                        )
                                        Text(
                                            text = product.categoria,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ChocolateFondant.copy(alpha = 0.6f),
                                        )
                                    }

                                    Text(
                                        text = if (product.precioAbierto) "Precio Libre" else formatPesos(product.precioCentavos),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandyBrown,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                // Panel de Selección de Cantidad, Importe y Notas
                if (selectedProduct != null) {
                    if (selectedProduct.precioAbierto) {
                        OutlinedTextField(
                            value = customPricePesos,
                            onValueChange = onCustomPriceChange,
                            label = { Text("Importe / Precio ($ MXN)") },
                            placeholder = { Text("Ej. 100.00 o 150.00") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandyBrown,
                                unfocusedBorderColor = ApricotIllusion,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Cantidad:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = VanillaCustard,
                                border = BorderStroke(1.dp, ApricotIllusion),
                                modifier = Modifier.size(30.dp),
                            ) {
                                IconButton(
                                    onClick = { if (quantity > 1) onQuantityChange(quantity - 1) },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Remove,
                                        contentDescription = "Menos",
                                        tint = BrandyBrown,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }

                            Text(
                                text = "$quantity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChocolateFondant,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )

                            Surface(
                                shape = CircleShape,
                                color = VanillaCustard,
                                border = BorderStroke(1.dp, ApricotIllusion),
                                modifier = Modifier.size(30.dp),
                            ) {
                                IconButton(
                                    onClick = { onQuantityChange(quantity + 1) },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "Más",
                                        tint = BrandyBrown,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = onNotesChange,
                        placeholder = { Text("Notas (ej. surtida, sin cebolla)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandyBrown,
                            unfocusedBorderColor = ApricotIllusion,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Botones Cancelar / Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChocolateFondant,
                        ),
                    ) {
                        Text("Cancelar")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = selectedProduct != null && !isSubmitting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Agregar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmCancelDialog(
    isOrderCancellation: Boolean,
    isCancelling: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE57373)),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isOrderCancellation) "Cancelar Comanda Completa" else "Cancelar Partida",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (isOrderCancellation)
                        "¿Estás seguro de que deseas cancelar esta comanda? La orden se marcará como cancelada y no podrá ser reabierta."
                    else
                        "¿Deseas cancelar este producto de la comanda? Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant,
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChocolateFondant,
                        ),
                    ) {
                        Text("Volver")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = !isCancelling,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}

@Composable
internal fun UserMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    val bgColor = when { isError -> Color(0xFFFFEBEE); isWarning -> Color(0xFFFFF3E0); else -> Color(0xFFE8F5E9) }
    val borderColor = when { isError -> Color(0xFFE57373); isWarning -> Color(0xFFFFB74D); else -> Color(0xFF81C784) }
    val textColor = when { isError -> Color(0xFFC62828); isWarning -> Color(0xFF8A4B08); else -> Color(0xFF2E7D32) }
    val icon = if (isError || isWarning) Icons.Outlined.Info else Icons.Outlined.CheckCircleOutline

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
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

private fun formatPesos(cents: Long): String {
    val pesos = cents.toDouble() / 100.0
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    return format.format(pesos)
}

@Composable
private fun OrdersScopeSwitcher(
    selectedTab: OrderScopeTab,
    todayCount: Int,
    historyCount: Int,
    onTabSelected: (OrderScopeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, ApricotIllusion), RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val todaySelected = selectedTab == OrderScopeTab.DEL_DIA
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (todaySelected) BrandyBrown else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onTabSelected(OrderScopeTab.DEL_DIA) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Comandas de Hoy",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (todaySelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (todaySelected) Color.White else ChocolateFondant.copy(alpha = 0.8f),
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (todaySelected) Color.White.copy(alpha = 0.25f) else ApricotIllusion.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = todayCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (todaySelected) Color.White else BrandyBrown,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        val historySelected = selectedTab == OrderScopeTab.HISTORIAL
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (historySelected) BrandyBrown else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onTabSelected(OrderScopeTab.HISTORIAL) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Historial",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (historySelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (historySelected) Color.White else ChocolateFondant.copy(alpha = 0.8f),
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = if (historySelected) Color.White.copy(alpha = 0.25f) else ApricotIllusion.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = historyCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (historySelected) Color.White else BrandyBrown,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
