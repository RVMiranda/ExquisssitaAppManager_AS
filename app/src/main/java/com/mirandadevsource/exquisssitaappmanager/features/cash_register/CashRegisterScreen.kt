package com.mirandadevsource.exquisssitaappmanager.features.cash_register

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun CashRegisterScreen(
    viewModel: CashRegisterViewModel,
    onNavigateToOrders: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    CashRegisterContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::onRefresh,
        onDateSelected = viewModel::onDateSelected,
        onSelectToday = viewModel::selectToday,
        onRequestPerformCut = viewModel::openConfirmCutDialog,
        onConfirmCut = viewModel::executeCut,
        onDismissConfirmDialog = viewModel::dismissConfirmCutDialog,
        onNavigateToOrders = onNavigateToOrders,
        modifier = modifier,
    )
}

@Composable
fun CashRegisterContent(
    uiState: CashRegisterUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onDateSelected: (String) -> Unit,
    onSelectToday: () -> Unit,
    onRequestPerformCut: () -> Unit,
    onConfirmCut: () -> Unit,
    onDismissConfirmDialog: () -> Unit,
    onNavigateToOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Cabecera Principal (Título + Refrescar a la izq + Realizar Corte a la der)
            CashRegisterHeader(
                onRefresh = onRefresh,
                isRefreshing = uiState.isRefreshing,
                onPerformCut = onRequestPerformCut,
                canPerformCut = uiState.canPerformCut,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Selector de Fecha de Negocio (Ancho completo con calendario Material 3)
            BusinessDateSelector(
                selectedDate = uiState.selectedDate,
                isToday = uiState.isToday,
                onDateSelected = onDateSelected,
                onSelectToday = onSelectToday,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Banner sin conexión
            if (!uiState.isOnline) {
                OfflineNoticeBanner(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Alerta de Comandas Abiertas (si existen órdenes sin cobrar)
            if (uiState.openOrdersCount > 0 && uiState.isToday) {
                OpenOrdersWarningBanner(
                    openOrdersCount = uiState.openOrdersCount,
                    onNavigateToOrders = onNavigateToOrders,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Alerta de límite de cortes alcanzado
            if (uiState.maxCutsReached && uiState.isToday) {
                MaxCutsNoticeBanner(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Contenido con Scroll
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Tarjeta de Resumen Acumulado del Día
                item {
                    DaySummaryCard(
                        summary = uiState.daySummary,
                        cutsCount = uiState.cuts.size,
                        selectedDate = uiState.selectedDate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Encabezado de la lista de cortes
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Cortes Registrados (${uiState.cuts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Máx. 5 por día",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }

                // Indicador de Carga
                if (uiState.isLoading && uiState.cuts.isEmpty()) {
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
                } else if (uiState.cuts.isEmpty()) {
                    item {
                        EmptyCutsCard(
                            selectedDate = uiState.selectedDate,
                            isToday = uiState.isToday,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    items(uiState.cuts, key = { it.id }) { closure ->
                        CutCard(
                            closure = closure,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        // Diálogo de Confirmación de Corte
        if (uiState.showConfirmCutDialog) {
            PerformCutConfirmationDialog(
                nextCutNumber = uiState.nextCutNumber,
                selectedDate = uiState.selectedDate,
                openOrdersCount = uiState.openOrdersCount,
                isSubmitting = uiState.isSubmitting,
                onConfirm = onConfirmCut,
                onDismiss = onDismissConfirmDialog,
                onNavigateToOrders = onNavigateToOrders,
            )
        }
    }
}

@Composable
private fun CashRegisterHeader(
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onPerformCut: () -> Unit,
    canPerformCut: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Corte de Caja",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Text(
                text = "Arqueo, conciliación de turnos y acumulado diario",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.size(40.dp),
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
                        contentDescription = "Refrescar cortes",
                        tint = BrandyBrown,
                    )
                }
            }

            Button(
                onClick = onPerformCut,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                    disabledContainerColor = BrandyBrown.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PointOfSale,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Realizar Corte",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessDateSelector(
    selectedDate: String,
    isToday: Boolean,
    onDateSelected: (String) -> Unit,
    onSelectToday: () -> Unit,
    modifier: Modifier = Modifier,
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

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            val year = cal.get(Calendar.YEAR)
                            val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                            val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                            onDateSelected("$year-$month-$day")
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(
                        text = "Cancelar",
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }
            },
            colors = DatePickerDefaults.colors(containerColor = Color.White),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = BrandyBrown,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = BrandyBrown,
                    todayContentColor = BrandyBrown,
                ),
            )
        }
    }

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = "Calendario",
                    tint = BrandyBrown,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isToday) "Fecha de Hoy (Turno en Curso)" else "Fecha Histórica Consultada",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = formatBusinessDate(selectedDate),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    TextButton(
                        onClick = onSelectToday,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Today,
                            contentDescription = null,
                            tint = BrandyBrown,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ir a Hoy",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                    }
                }

                IconButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Seleccionar fecha",
                        tint = BrandyBrown,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OfflineAmber.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OfflineAmber),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = OfflineAmber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Modo sin conexión: visualizando historial guardado en Room. Se requiere internet para realizar cortes.",
                style = MaterialTheme.typography.labelMedium,
                color = ChocolateFondant,
            )
        }
    }
}

@Composable
private fun OpenOrdersWarningBanner(
    openOrdersCount: Int,
    onNavigateToOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = OfflineAmber.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OfflineAmber),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = OfflineAmber,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hay $openOrdersCount comanda(s) abierta(s)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No se puede realizar un corte de caja mientras existan órdenes pendientes de cobro o cancelación.",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToOrders,
                border = BorderStroke(1.dp, BrandyBrown),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = "Ver Órdenes y Comandas",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun MaxCutsNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = BrandyBrown,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Se alcanzó el límite máximo de 5 cortes para esta fecha de negocio.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = ChocolateFondant,
            )
        }
    }
}

@Composable
private fun DaySummaryCard(
    summary: CashRegisterDaySummary?,
    cutsCount: Int,
    selectedDate: String,
    modifier: Modifier = Modifier,
) {
    val totalCents = summary?.totalCents ?: 0L
    val cashCents = summary?.cashCents ?: 0L
    val cardCents = summary?.cardCents ?: 0L
    val otherCents = summary?.otherCents ?: 0L
    val ordersCount = summary?.closedOrderCount ?: 0

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Resumen Acumulado del Día",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = BrandyBrown.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "$cutsCount / 5 Cortes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Global Destacado
            Column {
                Text(
                    text = "Total Ventas Conciliadas",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.65f),
                )
                Text(
                    text = formatCentsToPesos(totalCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandyBrown,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Desglose de Métodos de Pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FinancialMetricItem(
                    label = "Efectivo",
                    amount = formatCentsToPesos(cashCents),
                    color = ChocolateFondant,
                    modifier = Modifier.weight(1f),
                )
                FinancialMetricItem(
                    label = "Tarjeta",
                    amount = formatCentsToPesos(cardCents),
                    color = ChocolateFondant,
                    modifier = Modifier.weight(1f),
                )
                FinancialMetricItem(
                    label = "Otros",
                    amount = formatCentsToPesos(otherCents),
                    color = ChocolateFondant,
                    modifier = Modifier.weight(1f),
                )
                FinancialMetricItem(
                    label = "Comandas",
                    amount = ordersCount.toString(),
                    color = BrandyBrown,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FinancialMetricItem(
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ChocolateFondant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CutCard(
    closure: DailyClosure,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera de la tarjeta: Consecutivo + Hora de Cierre
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = BrandyBrown,
                    ) {
                        Text(
                            text = "Corte #${closure.cutNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${closure.closedOrderCount} comanda(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = ChocolateFondant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cerrado: ${formatIsoTime(closure.closedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horario de periodo cubierto
            Text(
                text = "Periodo: ${formatIsoTime(closure.periodStart)} — ${formatIsoTime(closure.periodEnd)}",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.8f),
            )

            // Actor / Responsable si está registrado
            if (!closure.closedBy.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = ChocolateFondant.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Responsable: ${closure.closedBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Desglose de totales del corte
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF9F7F4),
                border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Efectivo: ${formatCentsToPesos(closure.cashCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = "Tarjeta: ${formatCentsToPesos(closure.cardCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                        )
                        if (closure.otherCents > 0) {
                            Text(
                                text = "Otros: ${formatCentsToPesos(closure.otherCents)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChocolateFondant.copy(alpha = 0.8f),
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total del Periodo",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = formatCentsToPesos(closure.totalCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCutsCard(
    selectedDate: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.PointOfSale,
                contentDescription = null,
                tint = ApricotIllusion,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isToday) "Sin cortes registrados hoy" else "No se registraron cortes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isToday) {
                    "El primer corte registrará las ventas desde la medianoche hasta el momento del cierre."
                } else {
                    "No se encontraron cortes de caja guardados para la fecha seleccionada."
                },
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PerformCutConfirmationDialog(
    nextCutNumber: Int,
    selectedDate: String,
    openOrdersCount: Int,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToOrders: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BrandyBrown),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Realizar Corte de Caja",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                    if (!isSubmitting) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cerrar",
                                tint = ChocolateFondant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Detalle del corte
                Text(
                    text = "Estás a punto de registrar el Corte #$nextCutNumber de 5 para la fecha de negocio $selectedDate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Este corte consolidará e inmutabilizará las ventas registradas desde el corte anterior. El acumulado diario se actualizará automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.75f),
                )

                // Alerta si hay comandas abiertas
                if (openOrdersCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = OfflineAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, OfflineAmber),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = OfflineAmber,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bloqueado: $openOrdersCount comanda(s) abierta(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ChocolateFondant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Debes cobrar o cancelar todas las comandas en curso antes de realizar el corte.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ChocolateFondant.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = "Cancelar",
                            color = ChocolateFondant,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting && openOrdersCount == 0,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                            disabledContainerColor = BrandyBrown.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardando...")
                        } else {
                            Text(
                                text = "Confirmar Corte",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers
private fun formatCentsToPesos(cents: Long): String {
    val pesos = cents / 100.0
    return String.format(Locale.US, "$%,.2f", pesos)
}

private fun formatBusinessDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM, yyyy", Locale.forLanguageTag("es-MX"))
        date.format(formatter).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatIsoTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zdt = instant.atZone(ZoneId.of("America/Mexico_City"))
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("es-MX"))
        zdt.format(formatter)
    } catch (_: Exception) {
        try {
            val odt = OffsetDateTime.parse(isoString)
            val zdt = odt.atZoneSameInstant(ZoneId.of("America/Mexico_City"))
            val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("es-MX"))
            zdt.format(formatter)
        } catch (_: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }
}
