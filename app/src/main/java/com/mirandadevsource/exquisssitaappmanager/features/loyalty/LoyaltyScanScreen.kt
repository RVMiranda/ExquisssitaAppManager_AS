package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

data class LoyaltyCustomerUiModel(
    val id: String,
    val name: String,
    val phone: String?,
    val loyaltyPoints: Int,
    val totalVisits: Int,
    val qrTokenHash: String?,
)

@Composable
fun LoyaltyScanScreen(
    initialPhoneQuery: String? = null,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableIntStateOf(if (initialPhoneQuery != null) 1 else 0) }
    var phoneInput by remember { mutableStateOf(initialPhoneQuery.orEmpty()) }
    var manualQrInput by remember { mutableStateOf("") }
    var foundCustomer by remember { mutableStateOf<LoyaltyCustomerUiModel?>(null) }
    var visitRegisteredNotice by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VanillaCustard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pestañas Planas (Escanear QR vs Búsqueda por Teléfono)
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = VanillaCustard,
                contentColor = BrandyBrown,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTab),
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
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
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
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
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

            if (selectedTab == 0) {
                // Modo Escaneo QR
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Visor de Escaneo QR",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChocolateFondant,
                            fontWeight = FontWeight.Bold,
                        )

                        // Marco Guía del Escáner (Flat Outline)
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .border(
                                    width = 2.dp,
                                    color = BrandyBrown,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .background(
                                    color = VanillaCustard.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = BrandyBrown.copy(alpha = 0.7f),
                                modifier = Modifier.size(72.dp),
                            )
                        }

                        Text(
                            text = "Apunta la cámara del dispositivo al código QR del cliente para identificarlo de inmediato.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )

                        // Entrada manual de QR para pruebas en emulador/desarrollo
                        OutlinedTextField(
                            value = manualQrInput,
                            onValueChange = { manualQrInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Token o Hash QR (Prueba)") },
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
                                if (manualQrInput.isNotBlank()) {
                                    // Simulación de detección para maquetado
                                    foundCustomer = LoyaltyCustomerUiModel(
                                        id = "demo-cust-1",
                                        name = "Cliente Demo",
                                        phone = "9611234567",
                                        loyaltyPoints = 40,
                                        totalVisits = 4,
                                        qrTokenHash = manualQrInput.trim(),
                                    )
                                    visitRegisteredNotice = null
                                }
                            },
                            enabled = manualQrInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            Text("Simular Escaneo de QR")
                        }
                    }
                }
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

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) phoneInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Teléfono del cliente (10 dígitos)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (phoneInput.isNotBlank()) {
                                    foundCustomer = LoyaltyCustomerUiModel(
                                        id = "demo-cust-2",
                                        name = "Carlos López",
                                        phone = phoneInput.trim(),
                                        loyaltyPoints = 20,
                                        totalVisits = 2,
                                        qrTokenHash = "qr_hash_demo",
                                    )
                                    visitRegisteredNotice = null
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
                                if (phoneInput.isNotBlank()) {
                                    foundCustomer = LoyaltyCustomerUiModel(
                                        id = "demo-cust-2",
                                        name = "Carlos López",
                                        phone = phoneInput.trim(),
                                        loyaltyPoints = 20,
                                        totalVisits = 2,
                                        qrTokenHash = "qr_hash_demo",
                                    )
                                    visitRegisteredNotice = null
                                }
                            },
                            enabled = phoneInput.length >= 7,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
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

            // Tarjeta de Resultado del Cliente Encontrado
            foundCustomer?.let { customer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            verticalAlignment = Alignment.CenterVertically,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Visitas registradas: ${customer.totalVisits}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant.copy(alpha = 0.8f),
                            )
                        }

                        visitRegisteredNotice?.let { notice ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFF81C784)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = notice,
                                        color = Color(0xFF1B5E20),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    foundCustomer = customer.copy(
                                        loyaltyPoints = customer.loyaltyPoints + 10,
                                        totalVisits = customer.totalVisits + 1,
                                    )
                                    visitRegisteredNotice = "Visita diaria acreditada exitosamente (+10 puntos)."
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            ) {
                                Text(
                                    text = "Registrar Visita (+10)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            OutlinedButton(
                                onClick = { /* Hook para canje de recompensas */ },
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

            Spacer(Modifier.height(16.dp))
        }
    }
}
