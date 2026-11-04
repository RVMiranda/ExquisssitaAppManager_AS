package com.mirandadevsource.exquisssitaappmanager.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.util.Locale

private val SuccessGreen = Color(0xFF2E7D32)
private val SuccessGreenBg = Color(0xFFE8F5E9)
private val SuccessGreenBorder = Color(0xFFA5D6A7)

private val WarningAmberBg = Color(0xFFFFF8E1)
private val WarningAmberBorder = Color(0xFFFFE082)

@Composable
fun DashboardScreen(
    session: AppUserSession?,
    viewModel: DashboardViewModel,
    onNavigateToLoyalty: () -> Unit,
    onNavigateToTables: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToCashRegister: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToPayroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Encabezado de Saludo y Turno Operativo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hola, ${session?.employeeName?.ifBlank { "Personal" } ?: "Personal"}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ChocolateFondant,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ApricotIllusion),
                            color = Color.White.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = session?.role?.displayName.orEmpty().uppercase(),
                                color = BrandyBrown,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }

                        if (session?.isOffline == true) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, OfflineAmber),
                                color = OfflineAmber.copy(alpha = 0.15f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        tint = OfflineAmber,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Sin conexión",
                                        color = OfflineAmber,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.refreshDashboard() },
                    enabled = !uiState.isLoading && !uiState.isRefreshing,
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = BrandyBrown,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Actualizar panel",
                            tint = BrandyBrown,
                        )
                    }
                }
            }

            // Mensajes de confirmación o alerta
            if (uiState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.dismissUserMessage() }
                                .padding(4.dp),
                        )
                    }
                }
            }

            if (uiState.userMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreenBg,
                    border = BorderStroke(1.dp, SuccessGreenBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.userMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "OK",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.dismissUserMessage() }
                                .padding(4.dp),
                        )
                    }
                }
            }

            // 2. Hero Card: Escaneo y Fidelización QR de Clientes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLoyalty() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BrandyBrown),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, BrandyBrown),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Escanear QR de Cliente",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Suma +10 puntos o consulta recompensas",
                                color = ApricotIllusion,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = { onNavigateToLoyalty() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = BrandyBrown,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Abrir Escáner",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 3. Métricas Rápidas Operativas (Mesas, Órdenes, Ventas, Clientes)
            Text(
                text = "Resumen de Operación",
                style = MaterialTheme.typography.titleMedium,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Mesas Salón",
                    value = uiState.tableAvailabilityLabel,
                    detail = if (uiState.totalTables > 0) "Disponibles" else "Sin configurar",
                    icon = Icons.Outlined.TableRestaurant,
                    onClick = onNavigateToTables,
                )
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Órdenes Hoy",
                    value = uiState.todayOrdersCount.toString(),
                    detail = if (uiState.openOrdersCount > 0) "${uiState.openOrdersCount} abiertas" else "Sin órdenes vivas",
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    onClick = onNavigateToOrders,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Ventas Hoy",
                    value = formatMoney(uiState.todaySalesCents),
                    detail = if (uiState.todayPaymentsCount > 0) "${uiState.todayPaymentsCount} pagos registrados" else "Corte sin cobros",
                    icon = Icons.Outlined.PointOfSale,
                    onClick = onNavigateToCashRegister,
                )
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Visitas Clientes",
                    value = uiState.todayVisitsCount.toString(),
                    detail = "+10 pts c/u",
                    icon = Icons.Outlined.Loyalty,
                    onClick = onNavigateToLoyalty,
                )
            }

            // 4. Apartado de Prioridades y Alertas del Día
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Prioridades del Día",
                    style = MaterialTheme.typography.titleMedium,
                    color = ChocolateFondant,
                    fontWeight = FontWeight.Bold,
                )

                if (uiState.hasPendingSyncAlert) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OfflineAmber.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, OfflineAmber),
                    ) {
                        Text(
                            text = "${uiState.pendingSyncCount} pendientes",
                            color = BrandyBrown,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Prioridad 1: Inventario / Stock Bajo
                PriorityAlertCard(
                    isAlert = uiState.hasLowStockAlert,
                    alertTitle = "${uiState.lowStockProducts.size} producto(s) con stock bajo",
                    alertSubtitle = uiState.lowStockProducts.take(3).joinToString(separator = ", ") {
                        "${it.name} (${it.currentStock} disp.)"
                    }.let { if (uiState.lowStockProducts.size > 3) "$it..." else it },
                    healthyTitle = "Inventario en niveles óptimos",
                    healthySubtitle = "Todos los productos controlados sobre su stock mínimo",
                    alertIcon = Icons.Outlined.WarningAmber,
                    healthyIcon = Icons.Outlined.CheckCircleOutline,
                    actionLabel = "Ver Inventario",
                    onAction = onNavigateToInventory,
                )

                // Prioridad 2: Nómina Programada para Hoy
                PriorityAlertCard(
                    isAlert = uiState.hasPayrollAlert,
                    alertTitle = "${uiState.employeesToPayToday.size} empleado(s) por pagar hoy",
                    alertSubtitle = uiState.employeesToPayToday.joinToString(separator = ", ") {
                        "${it.name} (${formatMoney(it.wageCents)})"
                    },
                    healthyTitle = "Nómina al día para hoy",
                    healthySubtitle = "No hay pagos de personal pendientes programados para hoy",
                    alertIcon = Icons.Outlined.Payments,
                    healthyIcon = Icons.Outlined.CheckCircleOutline,
                    actionLabel = "Ver Nómina",
                    onAction = onNavigateToPayroll,
                )

                // Prioridad 3: Sincronización de Datos (Outbox)
                PriorityAlertCard(
                    isAlert = uiState.hasPendingSyncAlert,
                    alertTitle = "${uiState.pendingSyncCount} operaciones pendientes de sincronizar",
                    alertSubtitle = "Comandas, cobros o ajustes guardados localmente esperando conexión",
                    healthyTitle = "Sincronización al día",
                    healthySubtitle = "Todos los cambios locales están respaldados en la nube",
                    alertIcon = Icons.Outlined.Sync,
                    healthyIcon = Icons.Outlined.CloudDone,
                    actionLabel = if (uiState.isSyncing) "Sincronizando..." else "Sincronizar ahora",
                    actionEnabled = !uiState.isSyncing,
                    onAction = { viewModel.syncPendingData() },
                    secondaryActionLabel = if (uiState.hasPendingSyncAlert && !uiState.isSyncing) "Limpiar bloqueados" else null,
                    onSecondaryAction = { viewModel.cleanBlockedCommands() },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChocolateFondant.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandyBrown,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = BrandyBrown,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PriorityAlertCard(
    isAlert: Boolean,
    alertTitle: String,
    alertSubtitle: String,
    healthyTitle: String,
    healthySubtitle: String,
    alertIcon: ImageVector,
    healthyIcon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val bgColor = if (isAlert) WarningAmberBg else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isAlert) WarningAmberBorder else ApricotIllusion
    val iconTint = if (isAlert) OfflineAmber else SuccessGreen
    val icon = if (isAlert) alertIcon else healthyIcon

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isAlert) OfflineAmber.copy(alpha = 0.15f) else SuccessGreenBg,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAlert) alertTitle else healthyTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isAlert) alertSubtitle else healthySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isAlert) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandyBrown,
                        ),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        Text(
                            text = secondaryActionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandyBrown,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable(enabled = actionEnabled, onClick = onSecondaryAction)
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMoney(cents: Long): String {
    val pesos = cents / 100.0
    return String.format(Locale.US, "$%,.2f", pesos)
}
