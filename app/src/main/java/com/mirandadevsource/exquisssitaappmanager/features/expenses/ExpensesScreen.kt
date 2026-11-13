package com.mirandadevsource.exquisssitaappmanager.features.expenses

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.core.utils.CurrencyFormatter
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseCategories
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ApricotIllusion),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Encabezado
            item {
                ExpensesHeader(
                    isOnline = uiState.isOnline,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::onRefresh,
                    onOpenNewExpense = viewModel::openCreateDialog,
                )
            }

            // Selector de Fecha Comercial
            item {
                ExpensesDateSelector(
                    selectedDate = uiState.selectedDate,
                    isToday = uiState.isToday,
                    onDateSelected = viewModel::onDateSelected,
                    onSelectToday = viewModel::selectToday,
                )
            }

            // Resumen de Egresos y Rentabilidad
            item {
                ExpensesSummarySection(
                    totalExpensesCents = uiState.totalExpensesCents,
                    cashExpensesCents = uiState.cashExpensesCents,
                    otherExpensesCents = uiState.otherExpensesCents,
                    netProfitCents = uiState.profitSummary?.netProfitCents,
                    totalSalesCents = uiState.profitSummary?.totalSalesCents,
                )
            }

            // Filtro por Categoría
            item {
                CategoryFilterRow(
                    selectedCategory = uiState.selectedCategoryFilter,
                    onSelectCategory = viewModel::setCategoryFilter,
                )
            }

            // Título de la lista
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Gastos Registrados (${uiState.filteredExpenses.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }
            }

            // Lista de gastos o estados vacíos / cargando
            if (uiState.isLoading && uiState.expenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = BrandyBrown,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            } else if (uiState.filteredExpenses.isEmpty()) {
                item {
                    EmptyExpensesCard(
                        isFilterActive = !uiState.selectedCategoryFilter.isNullOrBlank(),
                        onClearFilter = { viewModel.setCategoryFilter(null) },
                        onRegisterExpense = viewModel::openCreateDialog,
                    )
                }
            } else {
                items(uiState.filteredExpenses, key = { it.localId }) { expense ->
                    ExpenseCard(
                        expense = expense,
                        onEdit = { viewModel.openEditDialog(expense) },
                        onDelete = { viewModel.requestDelete(expense) },
                    )
                }
            }
        }

        // Botón flotante para nuevo gasto
        FloatingActionButton(
            onClick = viewModel::openCreateDialog,
            containerColor = BrandyBrown,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Registrar nuevo gasto",
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        // Modal de Captura / Edición
        if (uiState.isFormDialogOpen) {
            ExpenseFormDialog(
                initialExpense = uiState.editingExpense,
                templates = uiState.templates,
                isSaving = uiState.isSaving,
                onDismiss = viewModel::closeFormDialog,
                onSave = viewModel::saveExpense,
            )
        }

        // Diálogo de Confirmación de Eliminación
        uiState.expenseToDelete?.let { expense ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = {
                    Text(
                        text = "Eliminar Gasto",
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                },
                text = {
                    Text(
                        text = "¿Deseas eliminar el gasto '${expense.concept}' por ${CurrencyFormatter.formatCents(expense.amountCents)}?",
                        color = ChocolateFondant,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text("Cancelar", color = BrandyBrown)
                    }
                },
                containerColor = ApricotIllusion,
            )
        }
    }
}

@Composable
private fun ExpensesHeader(
    isOnline: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenNewExpense: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Control de Gastos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Conciliación de egresos operativos",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )
                if (!isOnline) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = OfflineAmber.copy(alpha = 0.2f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = "Modo local sin conexión",
                                tint = OfflineAmber,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sin conexión",
                                style = MaterialTheme.typography.labelSmall,
                                color = OfflineAmber,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = BrandyBrown,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Actualizar gastos",
                    tint = BrandyBrown,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensesDateSelector(
    selectedDate: String,
    isToday: Boolean,
    onDateSelected: (String) -> Unit,
    onSelectToday: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = remember(selectedDate) {
            try {
                val parts = selectedDate.split("-")
                if (parts.size == 3) {
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    cal.clear()
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    cal.timeInMillis
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val instant = Instant.ofEpochMilli(millis)
                            val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
                            val isoDate = zonedDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            onDateSelected(isoDate)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Aceptar", color = BrandyBrown, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar", color = ChocolateFondant)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = ApricotIllusion),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = ChocolateFondant,
                    headlineContentColor = ChocolateFondant,
                    selectedDayContainerColor = BrandyBrown,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = BrandyBrown,
                    todayContentColor = BrandyBrown,
                ),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showDatePicker = true },
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = "Cambiar fecha",
                    tint = BrandyBrown,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = formatHumanDate(selectedDate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                    Text(
                        text = if (isToday) "Día de hoy" else selectedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }
            }

            if (!isToday) {
                TextButton(
                    onClick = onSelectToday,
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandyBrown),
                ) {
                    Text("Ir a Hoy", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExpensesSummarySection(
    totalExpensesCents: Long,
    cashExpensesCents: Long,
    otherExpensesCents: Long,
    netProfitCents: Long?,
    totalSalesCents: Long?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila 1: Total Gastos y Desglose
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total Egresos del Día",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = CurrencyFormatter.formatCents(totalExpensesCents),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "En Efectivo de Caja",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = CurrencyFormatter.formatCents(cashExpensesCents),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ChocolateFondant,
                    )
                    if (otherExpensesCents > 0L) {
                        Text(
                            text = "Transferencia/Otro: ${CurrencyFormatter.formatCents(otherExpensesCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Fila 2: Indicador de Rentabilidad Real si hay ventas del día
            if (totalSalesCents != null && totalSalesCents > 0L && netProfitCents != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (netProfitCents >= 0L) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    border = BorderStroke(
                        1.dp,
                        if (netProfitCents >= 0L) Color(0xFF81C784) else Color(0xFFE57373),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (netProfitCents >= 0L) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                contentDescription = null,
                                tint = if (netProfitCents >= 0L) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ganancia Neta Real (Ventas - Egresos):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (netProfitCents >= 0L) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            )
                        }

                        Text(
                            text = CurrencyFormatter.formatCents(netProfitCents),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (netProfitCents >= 0L) Color(0xFF2E7D32) else Color(0xFFC62828),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text("Todas") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BrandyBrown,
                selectedLabelColor = Color.White,
                containerColor = Color.White,
                labelColor = ChocolateFondant,
            ),
        )

        ExpenseCategories.ALL.forEach { cat ->
            FilterChip(
                selected = selectedCategory.equals(cat, ignoreCase = true),
                onClick = { onSelectCategory(cat) },
                label = { Text(cat) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandyBrown,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = ChocolateFondant,
                ),
            )
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = expense.concept,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Badge de Categoría
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApricotIllusion,
                        ) {
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandyBrown,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        // Badge de Método de Pago
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF5F5F5),
                        ) {
                            Text(
                                text = expense.paymentMethod.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        // Indicador de cantidad x precio unitario
                        if (expense.quantity != null && expense.unitPriceCents != null) {
                            val qtyStr = if (expense.quantity % 1.0 == 0.0) {
                                expense.quantity.toLong().toString()
                            } else {
                                String.format(Locale.US, "%.2f", expense.quantity)
                            }
                            Text(
                                text = "$qtyStr x ${CurrencyFormatter.formatCents(expense.unitPriceCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChocolateFondant.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Notas opcionales
                    if (!expense.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }

                    // Indicador offline si está pendiente
                    if (expense.isPendingSync) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = OfflineAmber,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pendiente de sincronizar",
                                style = MaterialTheme.typography.labelSmall,
                                color = OfflineAmber,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Monto y Acciones a la derecha
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCents(expense.amountCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Editar gasto",
                                tint = BrandyBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Eliminar gasto",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyExpensesCard(
    isFilterActive: Boolean,
    onClearFilter: () -> Unit,
    onRegisterExpense: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = BrandyBrown.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFilterActive) "No hay gastos con la categoría seleccionada" else "No hay gastos registrados en este día",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFilterActive) "Intenta limpiar el filtro para ver todos los egresos." else "Registra compras de insumos, tortillas, refrescos o servicios para conciliar tu caja.",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isFilterActive) {
                OutlinedButton(onClick = onClearFilter) {
                    Text("Ver todas las categorías", color = BrandyBrown)
                }
            } else {
                Button(
                    onClick = onRegisterExpense,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar Primer Gasto")
                }
            }
        }
    }
}

private fun formatHumanDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-MX"))
        date.format(formatter).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dateStr
    }
}
