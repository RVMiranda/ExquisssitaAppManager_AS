package com.mirandadevsource.exquisssitaappmanager.features.payments

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Remove
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ServiceType
import com.mirandadevsource.exquisssitaappmanager.features.orders.UserMessageBanner
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    uiState: PaymentUiState,
    onBack: () -> Unit,
    onSplitModeSelected: (SplitMode) -> Unit,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onItemQuantityChanged: (String, Int) -> Unit,
    onSelectAllItems: () -> Unit,
    onClearItemSelection: () -> Unit,
    onSplitCountChanged: (Int) -> Unit,
    onCashReceivedChanged: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onSubmitPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = uiState.order

    if (order == null) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = VanillaCustard,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No se ha seleccionado una orden para cobrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // 1. Banner informativo / error
            val bannerMsg = uiState.errorMessage ?: uiState.successMessage
            if (bannerMsg != null) {
                item {
                    UserMessageBanner(
                        message = bannerMsg,
                        isError = uiState.errorMessage != null,
                        onDismiss = onDismissMessage,
                        isWarning = uiState.lastPaymentCommand?.status == PaymentStatus.PENDING_SYNC && uiState.errorMessage == null,
                    )
                }
            }

            // 2. Resumen financiero de la comanda
            item {
                OrderFinancialSummaryCard(uiState = uiState)
            }

            // Si la orden ya está completamente pagada
            if (uiState.isFullyPaid) {
                item {
                    FullyPaidCard(onBack = onBack)
                }
            } else {
                // 3. Segmentador de Modos de División
                item {
                    SplitModeSelector(
                        currentMode = uiState.splitMode,
                        onModeSelected = onSplitModeSelected,
                    )
                }

                // 4. Detalle interactivo según el modo elegido
                when (uiState.splitMode) {
                    SplitMode.FULL -> {
                        item {
                            FullModeInfoCard(pendingCents = uiState.pendingBalanceCents)
                        }
                    }
                    SplitMode.BY_ITEMS -> {
                        item {
                            ByItemsHeader(
                                onSelectAll = onSelectAllItems,
                                onClearAll = onClearItemSelection,
                            )
                        }
                        items(uiState.pendingDetails, key = { it.id }) { item ->
                            val selectedQty = uiState.selectedItemQuantities[item.id] ?: 0
                            val maxQty = uiState.lineRemainingQuantity(item.id)
                            val productName = uiState.productMap[item.productId]?.nombre
                                ?: "Producto (${item.productId.take(6)})"
                            PaymentItemRow(
                                item = item,
                                productName = productName,
                                selectedQuantity = selectedQty,
                                maxQuantity = maxQty,
                                onQuantityChange = { delta -> onItemQuantityChanged(item.id, delta) },
                            )
                        }
                    }
                    SplitMode.EQUAL_SPLIT -> {
                        item {
                            EqualSplitControls(
                                splitCount = uiState.splitCount,
                                totalBalanceCents = uiState.pendingBalanceCents,
                                shareCents = uiState.calculatedPaymentAmountCents,
                                onCountChange = onSplitCountChanged,
                            )
                        }
                    }
                }

                // 5. Selector de Método de Pago
                item {
                    PaymentMethodPicker(
                        selectedMethod = uiState.selectedMethod,
                        onMethodSelected = onPaymentMethodSelected,
                    )
                }

                // 6. Calculadora de Efectivo si aplica
                if (uiState.selectedMethod == PaymentMethod.CASH) {
                    item {
                        CashCalculatorCard(
                            targetAmountCents = uiState.calculatedPaymentAmountCents,
                            cashInput = uiState.cashReceivedInput,
                            changeCents = uiState.cashChangeCents,
                            onCashChanged = onCashReceivedChanged,
                        )
                    }
                }

                // 7. Botón de Acción Principal de Cobro
                item {
                    SubmitPaymentButton(
                        amountCents = uiState.calculatedPaymentAmountCents,
                        canSubmit = uiState.canSubmitPayment,
                        isSubmitting = uiState.isSubmitting,
                        onSubmit = onSubmitPayment,
                    )
                }
            }

            // 8. Historial de Pagos Previos
            if (uiState.payments.isNotEmpty()) {
                item {
                    PaymentHistorySection(payments = uiState.payments)
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Ficha de resumen de saldos de la orden.
 */
@Composable
private fun OrderFinancialSummaryCard(
    uiState: PaymentUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Resumen de Cuenta",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                // Badge de saldo
                val (badgeBg, badgeFg, badgeText) = when {
                    uiState.isFullyPaid -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Liquidada")
                    uiState.paidAmountCents > 0L -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "Parcial")
                    else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Pendiente")
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeFg.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FinancialMetricColumn(label = "Total Orden", valueCents = uiState.totalOrderCents, color = ChocolateFondant)
                FinancialMetricColumn(label = "Pagado", valueCents = uiState.paidAmountCents, color = Color(0xFF2E7D32))
                FinancialMetricColumn(label = "Saldo Pendiente", valueCents = uiState.pendingBalanceCents, color = BrandyBrown, isHighlight = true)
            }
        }
    }
}

@Composable
private fun FinancialMetricColumn(
    label: String,
    valueCents: Long,
    color: Color,
    isHighlight: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ChocolateFondant.copy(alpha = 0.6f),
        )
        Text(
            text = formatPesos(valueCents),
            style = if (isHighlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * Selector plano de modo de cobro.
 */
@Composable
private fun SplitModeSelector(
    currentMode: SplitMode,
    onModeSelected: (SplitMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Modo de Cobro",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SplitMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) BrandyBrown else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelected(mode) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else ChocolateFondant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ficha descriptiva para cobro Total.
 */
@Composable
private fun FullModeInfoCard(
    pendingCents: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Cobro Completo de Cuenta",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Text(
                    text = "Se liquidará el saldo pendiente total de ${formatPesos(pendingCents)}, cerrando la comanda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * Cabecera para modo Por Partidas.
 */
@Composable
private fun ByItemsHeader(
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Selecciona las unidades a cobrar:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onSelectAll,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ApricotIllusion),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Todos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onClearAll,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ApricotIllusion),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ChocolateFondant),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Limpiar", fontSize = 11.sp)
            }
        }
    }
}

/**
 * Fila interactiva para seleccionar cantidad de un artículo a pagar.
 */
@Composable
private fun PaymentItemRow(
    item: OrderDetail,
    productName: String,
    selectedQuantity: Int,
    maxQuantity: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (selectedQuantity > 0) BrandyBrown else ApricotIllusion),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatPesos(item.unitPriceCents)} c/u • Pendiente: $maxQuantity",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.65f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Selector de cantidad a pagar
                Surface(
                    shape = CircleShape,
                    color = VanillaCustard,
                    border = BorderStroke(1.dp, ApricotIllusion),
                    modifier = Modifier.size(26.dp),
                ) {
                    IconButton(
                        onClick = { onQuantityChange(-1) },
                        enabled = selectedQuantity > 0,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = "Menos",
                            tint = if (selectedQuantity > 0) BrandyBrown else Color.Gray,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Text(
                    text = "$selectedQuantity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Surface(
                    shape = CircleShape,
                    color = VanillaCustard,
                    border = BorderStroke(1.dp, ApricotIllusion),
                    modifier = Modifier.size(26.dp),
                ) {
                    IconButton(
                        onClick = { onQuantityChange(1) },
                        enabled = selectedQuantity < maxQuantity,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Más",
                            tint = if (selectedQuantity < maxQuantity) BrandyBrown else Color.Gray,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Text(
                    text = formatPesos(selectedQuantity.toLong() * item.unitPriceCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedQuantity > 0) BrandyBrown else ChocolateFondant.copy(alpha = 0.4f),
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * Controles de división equitativa entre comensales.
 */
@Composable
private fun EqualSplitControls(
    splitCount: Int,
    totalBalanceCents: Long,
    shareCents: Long,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Dividir entre personas:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = VanillaCustard,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        modifier = Modifier.size(28.dp),
                    ) {
                        IconButton(
                            onClick = { onCountChange(-1) },
                            enabled = splitCount > 2,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = "Menos personas",
                                tint = if (splitCount > 2) BrandyBrown else Color.Gray,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    Text(
                        text = "$splitCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    Surface(
                        shape = CircleShape,
                        color = VanillaCustard,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        modifier = Modifier.size(28.dp),
                    ) {
                        IconButton(
                            onClick = { onCountChange(1) },
                            enabled = splitCount < 20,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Más personas",
                                tint = if (splitCount < 20) BrandyBrown else Color.Gray,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ApricotIllusion.copy(alpha = 0.25f))
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Cuota por comensal (1 de $splitCount):",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "Se liquidará una parte igual",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                }

                Text(
                    text = formatPesos(shareCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandyBrown,
                )
            }
        }
    }
}

/**
 * Selector plano de Método de Pago.
 */
@Composable
private fun PaymentMethodPicker(
    selectedMethod: PaymentMethod,
    onMethodSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Método de Pago",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val methods = listOf(
                Triple(PaymentMethod.CASH, "Efectivo", Icons.Outlined.LocalAtm),
                Triple(PaymentMethod.CARD, "Tarjeta", Icons.Outlined.CreditCard),
                Triple(PaymentMethod.TRANSFER, "Transferencia", Icons.Outlined.AccountBalance),
            )

            methods.forEach { (method, name, icon) ->
                val isSelected = selectedMethod == method
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) BrandyBrown else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) BrandyBrown else ApricotIllusion),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onMethodSelected(method) },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else BrandyBrown,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else ChocolateFondant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculadora de cambio para cobro en efectivo.
 */
@Composable
private fun CashCalculatorCard(
    targetAmountCents: Long,
    cashInput: String,
    changeCents: Long,
    onCashChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Calculadora de Cambio (Efectivo)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ChocolateFondant,
            )
            Spacer(Modifier.height(8.dp))

            // Atajos rápidos de denominaciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val exactPesos = (targetAmountCents / 100.0)
                val shortcuts = listOf(
                    "Exacto" to String.format(Locale.US, "%.2f", exactPesos),
                    "$50" to "50",
                    "$100" to "100",
                    "$200" to "200",
                    "$500" to "500",
                )

                shortcuts.forEach { (label, value) ->
                    OutlinedButton(
                        onClick = { onCashChanged(value) },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                    ) {
                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = cashInput,
                    onValueChange = onCashChanged,
                    label = { Text("Efectivo Recibido", color = ChocolateFondant.copy(alpha = 0.7f), fontSize = 12.sp) },
                    placeholder = { Text("0.00", color = ChocolateFondant.copy(alpha = 0.4f)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AttachMoney,
                            contentDescription = null,
                            tint = BrandyBrown,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                    ),
                    modifier = Modifier.weight(1f),
                )

                // Cambio a entregar
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cambio:",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChocolateFondant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = formatPesos(changeCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (changeCents > 0L) Color(0xFF2E7D32) else ChocolateFondant,
                    )
                }
            }
        }
    }
}

/**
 * Botón para procesar el pago.
 */
@Composable
private fun SubmitPaymentButton(
    amountCents: Long,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onSubmit,
        enabled = canSubmit,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ChocolateFondant,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFBCAAA4),
            disabledContentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Procesando pago...", fontWeight = FontWeight.Bold)
        } else {
            Icon(
                imageVector = Icons.Outlined.Payments,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Cobrar ${formatPesos(amountCents)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Ficha mostrada cuando la comanda ya está totalmente liquidada.
 */
@Composable
private fun FullyPaidCard(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE8F5E9),
        border = BorderStroke(1.dp, Color(0xFF81C784)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "¡Cuenta Totalmente Liquidada!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
            )
            Text(
                text = "No hay saldo pendiente por cobrar en esta comanda.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B5E20),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text("Volver a Órdenes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Sección de Historial de Transacciones / Pagos de la orden.
 */
@Composable
private fun PaymentHistorySection(
    payments: List<Payment>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Pagos Registrados (${payments.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )
        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            payments.forEach { payment ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ApricotIllusion.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "${payment.method.databaseValue} • ${payment.paidAt.take(16).replace("T", " ")}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ChocolateFondant,
                            )
                            val statusDesc = when (payment.status) {
                                PaymentStatus.CONFIRMED -> "Confirmado"
                                PaymentStatus.PENDING_SYNC -> "Pendiente de sincronizar"
                                PaymentStatus.REJECTED -> "Rechazado"
                            }
                            Text(
                                text = statusDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (payment.status) {
                                    PaymentStatus.CONFIRMED -> Color(0xFF2E7D32)
                                    PaymentStatus.PENDING_SYNC -> OfflineAmber
                                    PaymentStatus.REJECTED -> Color(0xFFC62828)
                                },
                            )
                        }

                        Text(
                            text = formatPesos(payment.amountCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                    }
                }
            }
        }
    }
}

private fun formatPesos(cents: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    return format.format(cents / 100.0)
}
