package com.mirandadevsource.exquisssitaappmanager.features.payroll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.WeeklyPayrollItem
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PayrollScreen(
    viewModel: PayrollViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Título de Pantalla
            Text(
                text = "Nómina Semanal",
                style = MaterialTheme.typography.headlineSmall,
                color = BrandyBrown,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Control de pagos semanales por cuota fija (periodo lunes a domingo)",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            // Feedback Banners
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

            // Selector de Semana (Navegador)
            WeekNavigatorCard(
                weekLabel = state.formattedWeekRange,
                onPrevious = { viewModel.previousWeek() },
                onNext = { viewModel.nextWeek() },
                onCurrent = { viewModel.currentWeek() },
            )

            Spacer(Modifier.height(14.dp))

            // Hero Card Financiero de la Semana
            PayrollSummaryHeroCard(
                totalCents = state.summary?.totalCents ?: 0L,
                pagadoCents = state.summary?.pagadoCents ?: 0L,
                pendienteCents = state.summary?.pendienteCents ?: 0L,
                progressFraction = state.progressFraction,
            )

            Spacer(Modifier.height(14.dp))

            // Lista de Empleados en Nómina
            if (state.isLoading && state.summary == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandyBrown)
                }
            } else {
                val items = state.summary?.items.orEmpty()
                if (items.isEmpty()) {
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
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = ChocolateFondant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = "No hay empleados activos registrados para este periodo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ChocolateFondant.copy(alpha = 0.6f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(items, key = { it.employee.id }) { payrollItem ->
                            PayrollEmployeeItemCard(
                                item = payrollItem,
                                onRegisterPayment = { viewModel.openPaymentModal(payrollItem) },
                            )
                        }
                    }
                }
            }
        }

        // Modal de Registro de Pago
        state.paymentModalItem?.let { modalItem ->
            RegisterPaymentDialog(
                item = modalItem,
                periodoInicio = state.weekStartMonday.toString(),
                fechaPago = state.paymentDateInput,
                notas = state.paymentNotesInput,
                isRegistering = state.isRegisteringPayment,
                onDateChange = { viewModel.updatePaymentDate(it) },
                onNotesChange = { viewModel.updatePaymentNotes(it) },
                onConfirm = { viewModel.confirmPayment() },
                onDismiss = { viewModel.closePaymentModal() },
            )
        }
    }
}

@Composable
private fun WeekNavigatorCard(
    weekLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VanillaCustard),
        border = BorderStroke(1.dp, ApricotIllusion),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = "Semana anterior",
                    tint = BrandyBrown,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Text(
                    text = "Semana Actual",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandyBrown,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onCurrent() }
                        .padding(top = 2.dp),
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Semana siguiente",
                    tint = BrandyBrown,
                )
            }
        }
    }
}

@Composable
private fun PayrollSummaryHeroCard(
    totalCents: Long,
    pagadoCents: Long,
    pendienteCents: Long,
    progressFraction: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BrandyBrown),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Nómina Semanal",
                    style = MaterialTheme.typography.labelMedium,
                    color = VanillaCustard.copy(alpha = 0.85f),
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ApricotIllusion.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = "${(progressFraction * 100).toInt()}% Liquidado",
                        style = MaterialTheme.typography.labelSmall,
                        color = VanillaCustard,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = formatPesos(totalCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = VanillaCustard,
            )

            // Barra de Progreso
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = ApricotIllusion,
                trackColor = VanillaCustard.copy(alpha = 0.3f),
            )

            // Desglose: Pagado vs Pendiente
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Pagado
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = ApricotIllusion,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Pagado",
                            style = MaterialTheme.typography.labelSmall,
                            color = VanillaCustard.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatPesos(pagadoCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = VanillaCustard,
                        )
                    }
                }

                // Pendiente
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = ApricotIllusion,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Por Pagar",
                            style = MaterialTheme.typography.labelSmall,
                            color = VanillaCustard.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatPesos(pendienteCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = VanillaCustard,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayrollEmployeeItemCard(
    item: WeeklyPayrollItem,
    onRegisterPayment: () -> Unit,
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
            // Fila Superior: Empleado y Badge de Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (item.isPaid) Color(0xFFE8F5E9) else ApricotIllusion.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.isPaid) Icons.Outlined.CheckCircle else Icons.Outlined.Person,
                                contentDescription = null,
                                tint = if (item.isPaid) Color(0xFF2E7D32) else BrandyBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Día habitual: ${item.employee.diaPagoNombre}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }

                // Badge Pagado / Pendiente
                if (item.isPaid) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                    ) {
                        Text(
                            text = "PAGADO",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = OfflineAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, OfflineAmber.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = "PENDIENTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = OfflineAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Importe y Detalles del Pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Importe semanal",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = formatPesos(item.employee.salarioCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                }

                if (item.isPaid) {
                    val payment = item.payment
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Fecha de pago",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = payment?.fechaPago ?: "Registrado",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                    }
                } else {
                    // Botón para Registrar Pago (Flat)
                    Button(
                        onClick = onRegisterPayment,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Registrar Pago",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Notas si existen
            item.payment?.notas?.let { notas ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ApricotIllusion.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Notes,
                            contentDescription = null,
                            tint = ChocolateFondant.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = notas,
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterPaymentDialog(
    item: WeeklyPayrollItem,
    periodoInicio: String,
    fechaPago: String,
    notas: String,
    isRegistering: Boolean,
    onDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!isRegistering) onDismiss() }) {
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
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Registrar Pago",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRegistering,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                // Resumen del Empleado y Monto
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
                            text = item.employee.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Monto semanal: ${formatPesos(item.employee.salarioCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                        Text(
                            text = "Periodo semanal (Lunes): $periodoInicio",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }
                }

                // Campo Fecha de Pago (AAAA-MM-DD)
                OutlinedTextField(
                    value = fechaPago,
                    onValueChange = onDateChange,
                    label = { Text("Fecha de Pago (AAAA-MM-DD)") },
                    singleLine = true,
                    enabled = !isRegistering,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                // Campo Notas Opcionales
                OutlinedTextField(
                    value = notas,
                    onValueChange = onNotesChange,
                    label = { Text("Notas (opcional, ej. Efectivo, SPEI)") },
                    singleLine = true,
                    enabled = !isRegistering,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )

                Spacer(Modifier.height(4.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isRegistering,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !isRegistering,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(
                                color = VanillaCustard,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Confirmar", fontWeight = FontWeight.Bold)
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
