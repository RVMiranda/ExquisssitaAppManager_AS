package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LoyaltyScanScreen(
    viewModel: LoyaltyViewModel,
    initialPhoneQuery: String? = null,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(initialPhoneQuery) {
        viewModel.initPhoneQuery(initialPhoneQuery)
    }

    // Auto-dismiss para notificaciones de éxito de 3 segundos
    LaunchedEffect(uiState.notificationEventId) {
        if (uiState.successNotification != null) {
            delay(3000)
            viewModel.dismissNotification()
        }
    }

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
            // Cabecera con botón de retorno al dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Volver al Dashboard",
                            tint = BrandyBrown,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Programa de Lealtad",
                        style = MaterialTheme.typography.titleLarge,
                        color = ChocolateFondant,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!uiState.isOnline) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Sin conexión",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Banner de Notificaciones de Error (persistente hasta descarte)
            AnimatedVisibility(
                visible = uiState.errorNotification != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                uiState.errorNotification?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = error,
                                color = Color(0xFFB71C1C),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.dismissNotification() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color(0xFFB71C1C),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Banner de Notificaciones de Advertencia
            AnimatedVisibility(
                visible = uiState.warningNotification != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                uiState.warningNotification?.let { warning ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, Color(0xFFFFE082)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = warning,
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.dismissNotification() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Banner de Notificaciones de Éxito
            AnimatedVisibility(
                visible = uiState.successNotification != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                uiState.successNotification?.let { success ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = success,
                                color = Color(0xFF1B5E20),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.dismissNotification() },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color(0xFF1B5E20),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Pestañas Planas (Escanear QR vs Búsqueda por Teléfono)
            SecondaryTabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = VanillaCustard,
                contentColor = BrandyBrown,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(uiState.selectedTab),
                        color = BrandyBrown,
                        height = 3.dp,
                    )
                },
                divider = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ApricotIllusion)
                    )
                },
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear QR", fontWeight = FontWeight.Bold)
                        }
                    },
                    selectedContentColor = BrandyBrown,
                    unselectedContentColor = ChocolateFondant.copy(alpha = 0.6f),
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Por Teléfono", fontWeight = FontWeight.Bold)
                        }
                    },
                    selectedContentColor = BrandyBrown,
                    unselectedContentColor = ChocolateFondant.copy(alpha = 0.6f),
                )
            }

            if (uiState.selectedTab == 0) {
                // Modo Escaneo QR
                QrScannerScreen(
                    onQrScanned = { viewModel.onQrScanned(it) },
                    isScanning = uiState.isSearching,
                    isOnline = uiState.isOnline,
                )
            } else {
                // Modo Búsqueda por Teléfono
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, ApricotIllusion),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Buscar por Número Telefónico",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChocolateFondant,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "Permite consultar el perfil y saldo del cliente registrado. La acreditación de visita requiere la lectura presencial de su código QR.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.75f),
                        )

                        OutlinedTextField(
                            value = uiState.phoneInput,
                            onValueChange = { viewModel.onPhoneInputChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Teléfono del cliente (10 dígitos)") },
                            supportingText = {
                                Text("${uiState.phoneInput.length}/10 dígitos")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (uiState.canSearchPhone) {
                                    viewModel.searchByPhone()
                                }
                            }),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandyBrown,
                                unfocusedBorderColor = ApricotIllusion,
                                cursorColor = BrandyBrown,
                                focusedTextColor = ChocolateFondant,
                                unfocusedTextColor = ChocolateFondant,
                            ),
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.searchByPhone()
                            },
                            enabled = uiState.canSearchPhone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            if (uiState.isSearching) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Consultando...")
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Consultar Ficha")
                            }
                        }
                    }
                }
            }

            // Tarjeta de Resultado del Cliente Identificado
            uiState.customer?.let { customer ->
                CustomerLoyaltyCard(
                    customer = customer,
                    hasScannedQr = uiState.hasScannedQrToken,
                    canRegisterVisit = uiState.canRegisterVisit,
                    isRegisteringVisit = uiState.isRegisteringVisit,
                    onRegisterVisit = { viewModel.registerVisit() },
                    onRedeem = { viewModel.onRedeemRewardsClicked() },
                    onClear = { viewModel.clearCustomer() },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Modal Informativo para Canje de Recompensas
    uiState.rewardNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRewardNotice() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Stars,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Canje de Recompensas",
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            },
            text = {
                Text(
                    text = notice,
                    color = ChocolateFondant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissRewardNotice() },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("Entendido")
                }
            },
            containerColor = VanillaCustard,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun CustomerLoyaltyCard(
    customer: Customer,
    hasScannedQr: Boolean,
    canRegisterVisit: Boolean,
    isRegisteringVisit: Boolean,
    onRegisterVisit: () -> Unit,
    onRedeem: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BrandyBrown),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = ChocolateFondant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = customer.phone?.let { "Tel: $it" } ?: "Sin teléfono registrado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }

                // Badge de Puntos
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ApricotIllusion.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, ApricotIllusion),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stars,
                            contentDescription = null,
                            tint = BrandyBrown,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${customer.loyaltyPoints} pts",
                            color = BrandyBrown,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // Advertencia si la ficha proviene de caché local
            if (customer.isCached) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ficha obtenida de caché local (${formatFetchedAt(customer.fetchedAt)}). Visitas y puntos pueden no estar actualizados.",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Visitas registradas: ${customer.totalVisits}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChocolateFondant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )

                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Limpiar ficha",
                        color = BrandyBrown,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (!hasScannedQr && !customer.isCached) {
                Text(
                    text = "Para registrar la visita diaria (+10 puntos), escanea el código QR del cliente desde la pestaña 'Escanear QR'.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChocolateFondant.copy(alpha = 0.65f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onRegisterVisit,
                    enabled = canRegisterVisit,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        disabledContainerColor = ApricotIllusion.copy(alpha = 0.4f),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    if (isRegisteringVisit) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Registrando...", fontSize = 12.sp)
                    } else {
                        Text(
                            text = "Registrar Visita (+10)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRedeem,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, BrandyBrown),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                ) {
                    Text(
                        text = "Canjear Puntos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatFetchedAt(instant: Instant?): String {
    if (instant == null) return "fecha previa"
    return try {
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        zonedDateTime.format(formatter)
    } catch (_: Exception) {
        "reciente"
    }
}
