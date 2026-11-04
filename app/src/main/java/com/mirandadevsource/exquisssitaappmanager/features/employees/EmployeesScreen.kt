package com.mirandadevsource.exquisssitaappmanager.features.employees

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanStatus
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesScreen(
    viewModel: EmployeesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.loadEmployees(isRefresh = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    // Encabezado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) "Directorio de Personal" else "Prestamos y Adelantos",
                                style = MaterialTheme.typography.headlineSmall,
                                color = BrandyBrown,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) {
                                    "Gestion de empleados de piso y cuotas de nomina semanal"
                                } else {
                                    "Registro y seguimiento de anticipos con deduccion diferida o total"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.loadEmployees(isRefresh = true) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Actualizar datos",
                                tint = BrandyBrown,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Mensajes de Feedback
                    state.errorMessage?.let { msg ->
                        FeedbackBanner(
                            message = msg,
                            isError = true,
                            onDismiss = { viewModel.clearMessages() },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    state.successMessage?.let { msg ->
                        FeedbackBanner(
                            message = msg,
                            isError = false,
                            onDismiss = { viewModel.clearMessages() },
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Selector de Pestañas: Directorio vs Préstamos
                    EmployeeScopeSwitcher(
                        selectedTab = state.selectedTab,
                        employeesCount = state.employees.size,
                        pendingLoansCount = state.pendingLoansCount,
                        onTabSelected = { viewModel.setTab(it) },
                    )

                    Spacer(Modifier.height(12.dp))

                    if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) {
                        // VISTA DIRECTORIO DE EMPLEADOS
                        FilterChipsRow(
                            selectedFilter = state.filter,
                            onSelectFilter = { viewModel.setFilter(it) },
                        )

                        Spacer(Modifier.height(12.dp))

                        if (state.isLoading && state.employees.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = BrandyBrown)
                            }
                        } else if (state.filteredEmployees.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonOff,
                                        contentDescription = null,
                                        tint = ChocolateFondant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = "No se encontraron empleados en esta vista.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ChocolateFondant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                items(state.filteredEmployees, key = { it.id }) { employee ->
                                    EmployeeCard(
                                        employee = employee,
                                        onEdit = { viewModel.openEditForm(employee) },
                                        onToggleActive = { viewModel.toggleActive(employee) },
                                        onNewLoan = {
                                            viewModel.setTab(EmployeeScreenTab.PRESTAMOS)
                                            viewModel.openCreateLoanDialog(employee.id)
                                        },
                                    )
                                }

                                if (state.isLoadingPage) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                color = BrandyBrown,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                } else if (state.hasMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.loadNextPage() },
                                                border = BorderStroke(1.dp, BrandyBrown),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                                            ) {
                                                Text("Cargar mas empleados")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // VISTA DE PRESTAMOS Y ADELANTOS
                        LoansKpiBanner(
                            totalPrestadoCents = state.totalPrestadoCents,
                            totalPendienteCents = state.totalPendingLoansCents,
                            pendingCount = state.pendingLoansCount,
                        )

                        Spacer(Modifier.height(10.dp))

                        LoanFilterChipsRow(
                            selectedFilter = state.loanFilter,
                            onSelectFilter = { viewModel.setLoanFilter(it) },
                        )

                        Spacer(Modifier.height(12.dp))

                        if (state.isLoadingLoans && state.loans.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = BrandyBrown)
                            }
                        } else if (state.filteredLoans.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = ChocolateFondant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = "No se encontraron prestamos registrados.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ChocolateFondant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                items(state.filteredLoans, key = { it.id }) { loan ->
                                    LoanCard(
                                        loan = loan,
                                        onAbonar = { viewModel.openAbonoDialog(loan) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FAB Flotante
            FloatingActionButton(
                onClick = {
                    if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) {
                        viewModel.openCreateForm()
                    } else {
                        viewModel.openCreateLoanDialog()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = BrandyBrown,
                contentColor = VanillaCustard,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) {
                            Icons.Outlined.PersonAdd
                        } else {
                            Icons.Outlined.Payments
                        },
                        contentDescription = if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) "Nuevo Empleado" else "Nuevo Prestamo",
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.selectedTab == EmployeeScreenTab.DIRECTORIO) "Nuevo" else "Prestar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            // Formulario Modal: Empleado
            state.formData?.let { form ->
                EmployeeFormDialog(
                    formData = form,
                    isSaving = state.isSaving,
                    onUpdateField = { transform -> viewModel.updateFormData(transform) },
                    onSave = { viewModel.saveEmployee() },
                    onDismiss = { viewModel.closeForm() },
                )
            }

            // Formulario Modal: Crear Préstamo
            state.createLoanForm?.let { loanForm ->
                CreateLoanDialog(
                    form = loanForm,
                    employees = state.employees.filter { it.activo },
                    isSaving = state.isSavingLoan,
                    errorMessage = state.loanFormError,
                    onUpdateField = { transform -> viewModel.updateCreateLoanForm(transform) },
                    onSave = { viewModel.submitCreateLoan() },
                    onDismiss = { viewModel.closeCreateLoanDialog() },
                )
            }

            // Formulario Modal: Registrar Abono Directo
            state.abonoForm?.let { abonoForm ->
                RegisterAbonoDialog(
                    form = abonoForm,
                    isSaving = state.isSavingAbono,
                    errorMessage = state.abonoFormError,
                    onUpdateField = { transform -> viewModel.updateAbonoForm(transform) },
                    onSave = { viewModel.submitAbono() },
                    onDismiss = { viewModel.closeAbonoDialog() },
                )
            }
        }
    }
}

@Composable
private fun EmployeeScopeSwitcher(
    selectedTab: EmployeeScreenTab,
    employeesCount: Int,
    pendingLoansCount: Int,
    onTabSelected: (EmployeeScreenTab) -> Unit,
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
        val isDirectorio = selectedTab == EmployeeScreenTab.DIRECTORIO
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (isDirectorio) BrandyBrown else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onTabSelected(EmployeeScreenTab.DIRECTORIO) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Directorio",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isDirectorio) FontWeight.Bold else FontWeight.Medium,
                    color = if (isDirectorio) VanillaCustard else ChocolateFondant,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDirectorio) ApricotIllusion.copy(alpha = 0.4f) else ApricotIllusion.copy(alpha = 0.3f),
                ) {
                    Text(
                        text = "$employeesCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDirectorio) VanillaCustard else BrandyBrown,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }

        val isPrestamos = selectedTab == EmployeeScreenTab.PRESTAMOS
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (isPrestamos) BrandyBrown else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onTabSelected(EmployeeScreenTab.PRESTAMOS) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Prestamos",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPrestamos) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPrestamos) VanillaCustard else ChocolateFondant,
                )
                if (pendingLoansCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isPrestamos) Color(0xFFFFB74D) else OfflineAmber,
                    ) {
                        Text(
                            text = "$pendingLoansCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandyBrown,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoansKpiBanner(
    totalPrestadoCents: Long,
    totalPendienteCents: Long,
    pendingCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ApricotIllusion.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Total Prestado",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.6f),
                )
                Text(
                    text = formatPesos(totalPrestadoCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Saldo Pendiente Total ($pendingCount activos)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.6f),
                )
                Text(
                    text = formatPesos(totalPendienteCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: EmployeeFilter,
    onSelectFilter: (EmployeeFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmployeeFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) BrandyBrown else VanillaCustard,
                border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectFilter(filter) },
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) VanillaCustard else ChocolateFondant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanFilterChipsRow(
    selectedFilter: LoanFilter,
    onSelectFilter: (LoanFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LoanFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) BrandyBrown else VanillaCustard,
                border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectFilter(filter) },
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) VanillaCustard else ChocolateFondant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmployeeCard(
    employee: Employee,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onNewLoan: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VanillaCustard),
        border = BorderStroke(1.dp, ApricotIllusion),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fila Superior: Nombre y Estado Activo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (employee.activo) ApricotIllusion.copy(alpha = 0.5f) else Color(0xFFE0E0E0),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = if (employee.activo) BrandyBrown else ChocolateFondant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        employee.telefono?.let { tel ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = ChocolateFondant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = tel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChocolateFondant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }

                // Badge de Estado
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (employee.activo) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, if (employee.activo) Color(0xFF81C784) else Color(0xFFE57373)),
                ) {
                    Text(
                        text = if (employee.activo) "Activo" else "Baja Logica",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (employee.activo) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Datos de Nómina: Cuota y Día de Pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Cuota semanal",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = formatPesos(employee.salarioCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ApricotIllusion.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Paga: ${employee.diaPagoNombre}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = ChocolateFondant,
                    )
                }
            }

            // Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (employee.activo) {
                    OutlinedButton(
                        onClick = onNewLoan,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = "Prestar",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Prestar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                OutlinedButton(
                    onClick = onToggleActive,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ChocolateFondant),
                ) {
                    Text(
                        text = if (employee.activo) "Desactivar" else "Reactivar",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Editar",
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Editar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanCard(
    loan: EmployeeLoan,
    onAbonar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VanillaCustard),
        border = BorderStroke(1.dp, ApricotIllusion),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fila Superior: Empleado y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ApricotIllusion.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = BrandyBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = loan.empleadoNombre ?: "Empleado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Fecha: ${loan.fechaPrestamo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }

                // Badge de Estado
                val (bgColor, borderColor, textColor, label) = when (loan.estado) {
                    LoanStatus.PENDIENTE -> Quadruple(Color(0xFFFFF3E0), Color(0xFFFFB74D), Color(0xFFE65100), "Pendiente")
                    LoanStatus.PAGO_PARCIAL -> Quadruple(Color(0xFFE3F2FD), Color(0xFF90CAF9), Color(0xFF1565C0), "Pago Parcial")
                    LoanStatus.LIQUIDADO -> Quadruple(Color(0xFFE8F5E9), Color(0xFF81C784), Color(0xFF2E7D32), "Liquidado")
                    LoanStatus.CANCELADO -> Quadruple(Color(0xFFEEEEEE), Color(0xFFBDBDBD), Color(0xFF616161), "Cancelado")
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Desglose de Montos
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ApricotIllusion.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Monto original",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "$${loan.montoPesos}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Abonado",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "$${loan.totalAbonadoPesos}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Saldo pendiente",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "$${loan.saldoPendientePesos}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (loan.saldoPendienteCentavos > 0L) BrandyBrown else Color(0xFF2E7D32),
                        )
                    }
                }
            }

            // Notas si existen
            loan.notas?.let { n ->
                Text(
                    text = "Nota: $n",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )
            }

            // Botón Abonar si está pendiente o en pago parcial
            if (loan.isPending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onAbonar,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = "Abonar",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Abonar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLoanDialog(
    form: CreateLoanFormData,
    employees: List<Employee>,
    isSaving: Boolean,
    errorMessage: String?,
    onUpdateField: ((CreateLoanFormData) -> CreateLoanFormData) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var employeeDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Nuevo Prestamo",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Text(
                    text = "Registra un adelanto de nómina o préstamo para el empleado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Selector de Empleado
                ExposedDropdownMenuBox(
                    expanded = employeeDropdownExpanded,
                    onExpandedChange = { if (!isSaving) employeeDropdownExpanded = it },
                ) {
                    val currentName = employees.firstOrNull { it.id == form.empleadoId }?.fullName ?: "Seleccionar empleado"
                    OutlinedTextField(
                        value = currentName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Empleado") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = employeeDropdownExpanded,
                        onDismissRequest = { employeeDropdownExpanded = false },
                        containerColor = VanillaCustard,
                    ) {
                        employees.forEach { emp ->
                            DropdownMenuItem(
                                text = { Text(emp.fullName, color = ChocolateFondant) },
                                onClick = {
                                    onUpdateField { it.copy(empleadoId = emp.id) }
                                    employeeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // Monto del Préstamo
                OutlinedTextField(
                    value = form.montoPesosText,
                    onValueChange = { value -> onUpdateField { it.copy(montoPesosText = value) } },
                    label = { Text("Monto del prestamo ($ MXN)") },
                    prefix = { Text("$ ", color = BrandyBrown, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("500.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Fecha (AAAA-MM-DD)
                OutlinedTextField(
                    value = form.fechaText,
                    onValueChange = { value -> onUpdateField { it.copy(fechaText = value) } },
                    label = { Text("Fecha del prestamo (AAAA-MM-DD)") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Notas
                OutlinedTextField(
                    value = form.notasText,
                    onValueChange = { value -> onUpdateField { it.copy(notasText = value) } },
                    label = { Text("Notas / Motivo (opcional)") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = VanillaCustard,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterAbonoDialog(
    form: AbonoLoanFormData,
    isSaving: Boolean,
    errorMessage: String?,
    onUpdateField: ((AbonoLoanFormData) -> AbonoLoanFormData) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val loan = form.loan ?: return

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Abonar a Prestamo",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Text(
                    text = "Descontar registrando abonos directos al prestamo del empleado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ApricotIllusion.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = loan.empleadoNombre ?: "Empleado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Saldo pendiente: $${loan.saldoPendientePesos}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Monto del abono
                OutlinedTextField(
                    value = form.montoPesosText,
                    onValueChange = { value -> onUpdateField { it.copy(montoPesosText = value) } },
                    label = { Text("Monto del abono ($ MXN)") },
                    prefix = { Text("$ ", color = BrandyBrown, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("200.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Notas
                OutlinedTextField(
                    value = form.notasText,
                    onValueChange = { value -> onUpdateField { it.copy(notasText = value) } },
                    label = { Text("Notas (ej. Pago directo en caja)") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = VanillaCustard,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Registrar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormDialog(
    formData: EmployeeFormData,
    isSaving: Boolean,
    onUpdateField: ((EmployeeFormData) -> EmployeeFormData) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val weekdays = remember {
        listOf(
            1 to "Lunes",
            2 to "Martes",
            3 to "Miercoles",
            4 to "Jueves",
            5 to "Viernes",
            6 to "Sabado",
            7 to "Domingo",
        )
    }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Título del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (formData.isEditing) "Editar Empleado" else "Nuevo Empleado",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Text(
                    text = "Personal operativo sin cuenta de acceso. Los pagos se registraran en base a esta cuota.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.7f),
                )

                // Nombre
                OutlinedTextField(
                    value = formData.nombre,
                    onValueChange = { value -> onUpdateField { it.copy(nombre = value) } },
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Apellido
                OutlinedTextField(
                    value = formData.apellido,
                    onValueChange = { value -> onUpdateField { it.copy(apellido = value) } },
                    label = { Text("Apellido") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Teléfono
                OutlinedTextField(
                    value = formData.telefono,
                    onValueChange = { value ->
                        if (value.length <= 10 && value.all { it.isDigit() }) {
                            onUpdateField { it.copy(telefono = value) }
                        }
                    },
                    label = { Text("Telefono (10 digitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Salario / Cuota Semanal en Pesos MXN
                OutlinedTextField(
                    value = formData.salarioPesosText,
                    onValueChange = { value -> onUpdateField { it.copy(salarioPesosText = value) } },
                    label = { Text("Cuota semanal ($ MXN)") },
                    prefix = { Text("$ ", color = BrandyBrown, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("1200.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !isSaving && formData.canEditSalary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )
                if (!formData.canEditSalary) {
                    Text(
                        text = "Solo un Administrador puede modificar esta cuota (un Gerente no puede auto-editar su salario).",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }

                // Selector de Día de Pago (1=Lunes..7=Domingo)
                ExposedDropdownMenuBox(
                    expanded = dayDropdownExpanded,
                    onExpandedChange = { if (!isSaving) dayDropdownExpanded = it },
                ) {
                    val currentDayLabel = weekdays.firstOrNull { it.first == formData.diaPago }?.second ?: "Sabado"
                    OutlinedTextField(
                        value = "Paga los $currentDayLabel",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Dia de Pago Programado") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = dayDropdownExpanded,
                        onDismissRequest = { dayDropdownExpanded = false },
                        containerColor = VanillaCustard,
                    ) {
                        weekdays.forEach { (diaInt, diaName) ->
                            DropdownMenuItem(
                                text = { Text(diaName, color = ChocolateFondant) },
                                onClick = {
                                    onUpdateField { it.copy(diaPago = diaInt) }
                                    dayDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // Switch Activo si está editando
                if (formData.isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Empleado Activo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                        Switch(
                            checked = formData.activo,
                            onCheckedChange = { checked -> onUpdateField { it.copy(activo = checked) } },
                            enabled = !isSaving,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VanillaCustard,
                                checkedTrackColor = BrandyBrown,
                                uncheckedThumbColor = ChocolateFondant,
                                uncheckedTrackColor = ApricotIllusion,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = VanillaCustard,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        border = BorderStroke(1.dp, if (isError) Color(0xFFE57373) else Color(0xFF81C784)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (isError) Color(0xFFC62828) else Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Color(0xFFC62828) else Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cerrar",
                    tint = if (isError) Color(0xFFC62828) else Color(0xFF2E7D32),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandyBrown,
    unfocusedBorderColor = ApricotIllusion,
    focusedLabelColor = BrandyBrown,
    unfocusedLabelColor = ChocolateFondant.copy(alpha = 0.6f),
    cursorColor = BrandyBrown,
    focusedTextColor = ChocolateFondant,
    unfocusedTextColor = ChocolateFondant,
)

private fun formatPesos(cents: Long): String {
    val pesos = cents.toDouble() / 100.0
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    return "${format.format(pesos)} MXN"
}
