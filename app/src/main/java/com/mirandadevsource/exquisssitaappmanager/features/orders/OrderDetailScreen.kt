package com.mirandadevsource.exquisssitaappmanager.features.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirandadevsource.exquisssitaappmanager.core.utils.DateUtils
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentLineStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: Order?,
    details: List<OrderDetail>,
    payments: List<Payment> = emptyList(),
    table: Table?,
    productMap: Map<String, Product>,
    isLoadingDetails: Boolean,
    userMessage: String? = null,
    isErrorMessage: Boolean = false,
    onDismissMessage: () -> Unit = {},
    onBack: () -> Unit,
    onOpenAddProduct: () -> Unit,
    onUpdateQuantity: (OrderDetail, Int) -> Unit,
    onPromptCancelItem: (OrderDetail) -> Unit,
    onPromptCancelOrder: (Order) -> Unit,
    onNavigateToCheckout: ((Order) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (order == null) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = VanillaCustard,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Orden no disponible o seleccionada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant,
                )
            }
        }
        return
    }

    val isOpen = order.status == OrderStatus.OPEN
    val totalCents = details.filter { it.paymentStatus != PaymentLineStatus.CANCELLED }.sumOf { it.lineTotalCents }
    val paidFromPayments = payments.filter { it.status == PaymentStatus.CONFIRMED }.sumOf { it.amountCents }
    val paidFromLines = details.filter { it.paymentStatus == PaymentLineStatus.PAID }.sumOf { it.lineTotalCents }
    val paidCents = maxOf(paidFromPayments, paidFromLines).coerceAtMost(totalCents)
    val pendingCents = (totalCents - paidCents).coerceAtLeast(0L)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Mensaje de acción / alerta
            if (!userMessage.isNullOrBlank()) {
                UserMessageBanner(
                    message = userMessage,
                    isError = isErrorMessage,
                    onDismiss = onDismissMessage,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Ficha de Resumen de la Comanda
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ApricotIllusion),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val serviceIcon: ImageVector = when (order.serviceType) {
                                ServiceType.TABLE -> Icons.Outlined.TableRestaurant
                                ServiceType.TAKEOUT -> Icons.Outlined.TakeoutDining
                                ServiceType.DELIVERY -> Icons.Outlined.DeliveryDining
                            }
                            Icon(
                                imageVector = serviceIcon,
                                contentDescription = null,
                                tint = BrandyBrown,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (order.serviceType) {
                                    ServiceType.TABLE -> "Servicio en Mesa ${table?.number ?: ""}".trim()
                                    ServiceType.TAKEOUT -> "Pedido Para Llevar"
                                    ServiceType.DELIVERY -> "Servicio a Domicilio"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ChocolateFondant,
                            )
                        }

                        // Badge de Estado
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = ChocolateFondant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apertura: ${DateUtils.formatOrderDateTime(order.openedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                        )
                    }

                    if (!order.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Notas: \"${order.notes}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Barra de Sección de Partidas y Botón Agregar Producto
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Artículos en Comanda (${details.count { it.paymentStatus != PaymentLineStatus.CANCELLED }})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )

                if (isOpen) {
                    Button(
                        onClick = onOpenAddProduct,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Agregar Producto",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Lista de Partidas / Artículos
            if (isLoadingDetails) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandyBrown, strokeWidth = 2.dp)
                }
            } else if (details.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
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
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Sin productos agregados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Utiliza el botón 'Agregar Producto' para registrar los platillos y bebidas del pedido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(details, key = { it.id }) { item ->
                        OrderDetailItemCard(
                            item = item,
                            product = productMap[item.productId],
                            isOrderOpen = isOpen,
                            onQuantityChange = { delta -> onUpdateQuantity(item, item.quantity + delta) },
                            onCancelItem = { onPromptCancelItem(item) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Resumen de Totales y Acciones Inferiores
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ApricotIllusion),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Subtotal Pendiente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = formatPesos(pendingCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                    }

                    if (paidCents > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Pagado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32),
                            )
                            Text(
                                text = formatPesos(paidCents),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total Comanda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = formatPesos(totalCents),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandyBrown,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Botones de Acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isOpen && paidCents == 0L) {
                            OutlinedButton(
                                onClick = { onPromptCancelOrder(order) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE57373)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFC62828),
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Cancelar Orden",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (isOpen && pendingCents > 0) {
                            Button(
                                onClick = { onNavigateToCheckout?.invoke(order) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandyBrown,
                                    contentColor = Color.White,
                                ),
                                elevation = ButtonDefaults.buttonElevation(0.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Payment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Cobrar Cuenta",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailItemCard(
    item: OrderDetail,
    product: Product?,
    isOrderOpen: Boolean,
    onQuantityChange: (Int) -> Unit,
    onCancelItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCancelled = item.paymentStatus == PaymentLineStatus.CANCELLED
    val isPaid = item.paymentStatus == PaymentLineStatus.PAID
    val isPending = item.paymentStatus == PaymentLineStatus.PENDING

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCancelled) Color(0xFFFBFBFB) else Color.White,
        border = BorderStroke(1.dp, if (isCancelled) Color(0xFFE0E0E0) else ApricotIllusion.copy(alpha = 0.7f)),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product?.nombre ?: "Producto (${item.productId.take(6)})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCancelled) ChocolateFondant.copy(alpha = 0.5f) else ChocolateFondant,
                        textDecoration = if (isCancelled) TextDecoration.LineThrough else null,
                    )
                    Text(
                        text = "${formatPesos(item.unitPriceCents)} c/u",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }

                // Badge de Estatus de Pago de la Partida
                val (lineBg, lineFg, lineText) = when (item.paymentStatus) {
                    PaymentLineStatus.PENDING -> Triple(OfflineAmber.copy(alpha = 0.15f), OfflineAmber, "Pendiente")
                    PaymentLineStatus.PAID -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Pagado")
                    PaymentLineStatus.CANCELLED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Cancelado")
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = lineBg,
                ) {
                    Text(
                        text = lineText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = lineFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            if (!item.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Nota: ${item.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandyBrown,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fila de Controles de Cantidad y Total de la Línea
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Selector de cantidad si la partida está pendiente y la comanda está abierta
                if (isOrderOpen && isPending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VanillaCustard,
                            border = BorderStroke(1.dp, ApricotIllusion),
                            modifier = Modifier.size(28.dp),
                        ) {
                            IconButton(
                                onClick = { onQuantityChange(-1) },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Remove,
                                    contentDescription = "Disminuir",
                                    tint = BrandyBrown,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Text(
                            text = "${item.quantity}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )

                        Surface(
                            shape = CircleShape,
                            color = VanillaCustard,
                            border = BorderStroke(1.dp, ApricotIllusion),
                            modifier = Modifier.size(28.dp),
                        ) {
                            IconButton(
                                onClick = { onQuantityChange(1) },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Aumentar",
                                    tint = BrandyBrown,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Botón cancelar partida
                        IconButton(
                            onClick = onCancelItem,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Cancelar partida",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Cantidad: ${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }

                Text(
                    text = formatPesos(item.lineTotalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCancelled) ChocolateFondant.copy(alpha = 0.4f) else BrandyBrown,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else null,
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
