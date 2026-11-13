package com.mirandadevsource.exquisssitaappmanager.features.expenses

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pestañas principales de navegación interna: Gastos vs Plantillas
            PrimaryTabRow(
                selectedTabIndex = if (uiState.selectedTab == ExpenseViewTab.GASTOS) 0 else 1,
                containerColor = Color.White,
                contentColor = BrandyBrown,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = uiState.selectedTab == ExpenseViewTab.GASTOS,
                    onClick = { viewModel.selectTab(ExpenseViewTab.GASTOS) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.selectedTab == ExpenseViewTab.GASTOS) BrandyBrown else ChocolateFondant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = "Gastos (${uiState.expenses.size})",
                                fontWeight = if (uiState.selectedTab == ExpenseViewTab.GASTOS) FontWeight.Bold else FontWeight.Medium,
                                color = if (uiState.selectedTab == ExpenseViewTab.GASTOS) BrandyBrown else ChocolateFondant.copy(alpha = 0.7f),
                            )
                        }
                    },
                )
                Tab(
                    selected = uiState.selectedTab == ExpenseViewTab.PLANTILLAS,
                    onClick = { viewModel.selectTab(ExpenseViewTab.PLANTILLAS) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (uiState.selectedTab == ExpenseViewTab.PLANTILLAS) BrandyBrown else ChocolateFondant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = "Plantillas (${uiState.allTemplates.size})",
                                fontWeight = if (uiState.selectedTab == ExpenseViewTab.PLANTILLAS) FontWeight.Bold else FontWeight.Medium,
                                color = if (uiState.selectedTab == ExpenseViewTab.PLANTILLAS) BrandyBrown else ChocolateFondant.copy(alpha = 0.7f),
                            )
                        }
                    },
                )
            }

            // Contenido según la pestaña activa
            when (uiState.selectedTab) {
                ExpenseViewTab.GASTOS -> {
                    ExpensesTabContent(
                        uiState = uiState,
                        onRefresh = viewModel::onRefresh,
                        onDateSelected = viewModel::onDateSelected,
                        onSelectToday = viewModel::selectToday,
                        onSelectCategory = viewModel::setCategoryFilter,
                        onOpenCreateExpense = viewModel::openCreateDialog,
                        onEditExpense = viewModel::openEditDialog,
                        onDeleteExpense = viewModel::requestDelete,
                    )
                }
                ExpenseViewTab.PLANTILLAS -> {
                    TemplatesTabContent(
                        uiState = uiState,
                        onRefresh = viewModel::onRefresh,
                        onSelectCategory = viewModel::setTemplateCategoryFilter,
                        onOpenCreateTemplate = viewModel::openCreateTemplateDialog,
                        onEditTemplate = viewModel::openEditTemplateDialog,
                        onDeleteTemplate = viewModel::requestDeleteTemplate,
                        onToggleActive = viewModel::toggleTemplateActive,
                    )
                }
            }
        }

        // Botón flotante dinámico según pestaña
        FloatingActionButton(
            onClick = {
                if (uiState.selectedTab == ExpenseViewTab.GASTOS) {
                    viewModel.openCreateDialog()
                } else {
                    viewModel.openCreateTemplateDialog()
                }
            },
            containerColor = BrandyBrown,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = if (uiState.selectedTab == ExpenseViewTab.GASTOS) "Registrar gasto" else "Nueva plantilla",
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        // Modal de Captura / Edición de Gasto
        if (uiState.isFormDialogOpen) {
            ExpenseFormDialog(
                initialExpense = uiState.editingExpense,
                templates = uiState.templates,
                isSaving = uiState.isSaving,
                onDismiss = viewModel::closeFormDialog,
                onSave = viewModel::saveExpense,
            )
        }

        // Modal de Captura / Edición de Plantilla
        if (uiState.isTemplateDialogOpen) {
            ExpenseTemplateFormDialog(
                initialTemplate = uiState.editingTemplate,
                isSaving = uiState.isSavingTemplate,
                onDismiss = viewModel::closeTemplateDialog,
                onSave = viewModel::saveTemplate,
            )
        }

        // Diálogo de Confirmación de Eliminación de Gasto
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
                containerColor = VanillaCustard,
            )
        }

        // Diálogo de Confirmación de Eliminación de Plantilla
        uiState.templateToDelete?.let { template ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDeleteTemplate,
                title = {
                    Text(
                        text = "Eliminar Plantilla",
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                },
                text = {
                    Text(
                        text = "¿Deseas eliminar la plantilla '${template.name}' (${template.category}) del catálogo frecuente?",
                        color = ChocolateFondant,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmDeleteTemplate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDeleteTemplate) {
                        Text("Cancelar", color = BrandyBrown)
                    }
                },
                containerColor = VanillaCustard,
            )
        }
    }
}

// =========================================================================
// Pestaña 1: Gastos del Día
// =========================================================================

@Composable
private fun ExpensesTabContent(
    uiState: ExpensesUiState,
    onRefresh: () -> Unit,
    onDateSelected: (String) -> Unit,
    onSelectToday: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onOpenCreateExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Encabezado
        item {
            ExpensesHeader(
                isOnline = uiState.isOnline,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
            )
        }

        // Selector de Fecha Comercial
        item {
            ExpensesDateSelector(
                selectedDate = uiState.selectedDate,
                isToday = uiState.isToday,
                onDateSelected = onDateSelected,
                onSelectToday = onSelectToday,
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
                onSelectCategory = onSelectCategory,
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
                    onClearFilter = { onSelectCategory(null) },
                    onRegisterExpense = onOpenCreateExpense,
                )
            }
        } else {
            items(uiState.filteredExpenses, key = { it.localId }) { expense ->
                ExpenseCard(
                    expense = expense,
                    onEdit = { onEditExpense(expense) },
                    onDelete = { onDeleteExpense(expense) },
                )
            }
        }
    }
}

// =========================================================================
// Pestaña 2: Plantillas de Gastos Frecuentes
// =========================================================================

@Composable
private fun TemplatesTabContent(
    uiState: ExpensesUiState,
    onRefresh: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onOpenCreateTemplate: () -> Unit,
    onEditTemplate: (ExpenseTemplate) -> Unit,
    onDeleteTemplate: (ExpenseTemplate) -> Unit,
    onToggleActive: (ExpenseTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Encabezado de plantillas
        item {
            TemplatesHeader(
                isOnline = uiState.isOnline,
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                onOpenNewTemplate = onOpenCreateTemplate,
            )
        }

        // Filtro por categoría de plantillas
        item {
            CategoryFilterRow(
                selectedCategory = uiState.selectedTemplateCategoryFilter,
                onSelectCategory = onSelectCategory,
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
                    text = "Catálogo de Insumos Frecuentes (${uiState.filteredTemplates.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
            }
        }

        // Lista de plantillas o estado vacío
        if (uiState.isLoading && uiState.allTemplates.isEmpty()) {
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
        } else if (uiState.filteredTemplates.isEmpty()) {
            item {
                EmptyTemplatesCard(
                    isFilterActive = !uiState.selectedTemplateCategoryFilter.isNullOrBlank(),
                    onClearFilter = { onSelectCategory(null) },
                    onCreateTemplate = onOpenCreateTemplate,
                )
            }
        } else {
            items(uiState.filteredTemplates, key = { it.localId }) { template ->
                TemplateCard(
                    template = template,
                    onEdit = { onEditTemplate(template) },
                    onDelete = { onDeleteTemplate(template) },
                    onToggleActive = { onToggleActive(template) },
                )
            }
        }
    }
}

@Composable
private fun TemplatesHeader(
    isOnline: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenNewTemplate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Plantillas de Gastos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Text(
                text = "Insumos habituales y precios pactados para captura ágil",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                        contentDescription = "Actualizar plantillas",
                        tint = BrandyBrown,
                    )
                }
            }

            Button(
                onClick = onOpenNewTemplate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nueva", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: ExpenseTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApricotIllusion.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = template.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandyBrown,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (template.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        ) {
                            Text(
                                text = if (template.isActive) "Activo" else "Inactivo",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (template.isActive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (template.unitPriceCents > 0L) {
                            CurrencyFormatter.formatCents(template.unitPriceCents)
                        } else {
                            "Precio libre"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (template.unitPriceCents > 0L) BrandyBrown else ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "por ${template.unitOfMeasure}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onToggleActive,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (template.isActive) "Desactivar" else "Activar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (template.isActive) ChocolateFondant.copy(alpha = 0.7f) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Editar plantilla",
                            tint = BrandyBrown,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar plantilla",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTemplatesCard(
    isFilterActive: Boolean,
    onClearFilter: () -> Unit,
    onCreateTemplate: () -> Unit,
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
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = BrandyBrown.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFilterActive) "Sin plantillas en esta categoría" else "No hay plantillas registradas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFilterActive) {
                    "Prueba seleccionando otra categoría"
                } else {
                    "Crea plantillas para compras habituales como tortillas, refrescos o gas"
                },
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isFilterActive) {
                OutlinedButton(
                    onClick = onClearFilter,
                    border = BorderStroke(1.dp, BrandyBrown),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                ) {
                    Text("Ver todas las categorías")
                }
            } else {
                Button(
                    onClick = onCreateTemplate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear Primera Plantilla")
                }
            }
        }
    }
}

// =========================================================================
// Componentes Reutilizables de Gastos
// =========================================================================

@Composable
private fun ExpensesHeader(
    isOnline: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
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
            colors = DatePickerDefaults.colors(containerColor = VanillaCustard),
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
        border = BorderStroke(1.dp, ApricotIllusion),
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
                        text = if (isToday) "Turno operativo de hoy" else "Consulta de histórico",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    TextButton(onClick = onSelectToday) {
                        Text(
                            text = "Ir a Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                    }
                }
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Abrir calendario",
                        tint = BrandyBrown,
                    )
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
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                }

                // Badge de Ganancia Neta Real o Balance del Día
                if (netProfitCents != null && ((totalSalesCents ?: 0L) > 0L || totalExpensesCents > 0L)) {
                    val isPositive = netProfitCents >= 0L
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, if (isPositive) Color(0xFF81C784) else Color(0xFFE57373)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                contentDescription = null,
                                tint = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = if ((totalSalesCents ?: 0L) == 0L) "Egresos sin venta" else if (isPositive) "Ganancia Neta" else "Pérdida",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = CurrencyFormatter.formatCents(netProfitCents),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Desglose por método de pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Efectivo (Sale de caja)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = CurrencyFormatter.formatCents(cashExpensesCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ChocolateFondant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Transferencia / Otros",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = CurrencyFormatter.formatCents(otherExpensesCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ChocolateFondant,
                    )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val isAllSelected = selectedCategory.isNullOrBlank()
        FilterChip(
            selected = isAllSelected,
            onClick = { onSelectCategory(null) },
            label = { Text("Todas") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BrandyBrown,
                selectedLabelColor = Color.White,
                containerColor = Color.White,
                labelColor = ChocolateFondant,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isAllSelected,
                borderColor = ApricotIllusion,
                selectedBorderColor = BrandyBrown,
            ),
        )

        ExpenseCategories.ALL.forEach { cat ->
            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(if (isSelected) null else cat) },
                label = { Text(cat) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandyBrown,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = ChocolateFondant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = ApricotIllusion,
                    selectedBorderColor = BrandyBrown,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.concept,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ApricotIllusion.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = expense.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandyBrown,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

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

                    if (!expense.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }

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
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCents(expense.amountCents),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Editar gasto",
                        tint = BrandyBrown,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Eliminar gasto",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
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
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = BrandyBrown.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFilterActive) "Sin gastos en esta categoría" else "No hay gastos registrados hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFilterActive) {
                    "Prueba cambiando el filtro de categoría"
                } else {
                    "Registra egresos como tortillas, refrescos, carbón o insumos del negocio"
                },
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isFilterActive) {
                OutlinedButton(
                    onClick = onClearFilter,
                    border = BorderStroke(1.dp, BrandyBrown),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                ) {
                    Text("Ver todas las categorías")
                }
            } else {
                Button(
                    onClick = onRegisterExpense,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar Primer Gasto")
                }
            }
        }
    }
}

private fun formatHumanDate(isoDate: String): String {
    return try {
        val localDate = LocalDate.parse(isoDate)
        val mxLocale = Locale.forLanguageTag("es-MX")
        val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", mxLocale)
        localDate.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(mxLocale) else it.toString() }
    } catch (_: Exception) {
        isoDate
    }
}
