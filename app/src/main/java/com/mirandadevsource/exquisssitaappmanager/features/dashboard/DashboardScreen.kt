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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

@Composable
fun DashboardScreen(
    session: AppUserSession?,
    onNavigateToLoyalty: (searchPhone: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var quickPhoneQuery by remember { mutableStateOf("") }

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
            // 1. Saludo y Encabezado de Turno
            Column {
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

            // 2. Hero Card: Escaneo y Fidelización QR
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLoyalty(null) },
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

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onNavigateToLoyalty(null) },
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

            // 3. Métricas Rápidas Operativas (Tarjetas Planas)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Visitas Hoy",
                    value = "0",
                    detail = "+10 pts c/u",
                    icon = Icons.Outlined.Loyalty,
                )
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Mesas Salón",
                    value = "0 / 12",
                    detail = "Disponibles",
                    icon = Icons.Outlined.TableRestaurant,
                )
            }

            // 4. Atajo Rápido: Búsqueda Manual por Teléfono
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, ApricotIllusion),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = null,
                            tint = BrandyBrown,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Búsqueda manual de cliente",
                            style = MaterialTheme.typography.titleSmall,
                            color = ChocolateFondant,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = "Si el cliente no tiene su código QR disponible, búscalo por su número telefónico.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChocolateFondant.copy(alpha = 0.8f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = quickPhoneQuery,
                            onValueChange = { if (it.length <= 10 && it.all(Char::isDigit)) quickPhoneQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Teléfono (10 dígitos)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (quickPhoneQuery.isNotBlank()) {
                                    onNavigateToLoyalty(quickPhoneQuery.trim())
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
                                if (quickPhoneQuery.isNotBlank()) {
                                    onNavigateToLoyalty(quickPhoneQuery.trim())
                                }
                            },
                            enabled = quickPhoneQuery.length >= 7,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandyBrown,
                                contentColor = Color.White,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Buscar",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
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
                style = MaterialTheme.typography.headlineMedium,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = BrandyBrown,
                fontSize = 11.sp,
            )
        }
    }
}
