package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

@Composable
fun QrScannerScreen(
    onQrScanned: (String) -> Unit,
    isScanning: Boolean,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var manualTokenInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, ApricotIllusion),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Visor de Escaneo QR",
                style = MaterialTheme.typography.titleMedium,
                color = ChocolateFondant,
                fontWeight = FontWeight.Bold,
            )

            if (!isOnline) {
                Surface(
                    color = ApricotIllusion.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "El escaneo QR y registro de visitas requieren conexión activa a internet.",
                        color = BrandyBrown,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Marco Guía del Escáner
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .border(
                        width = 2.dp,
                        color = if (hasCameraPermission) BrandyBrown else ApricotIllusion,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(
                        color = VanillaCustard.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = BrandyBrown,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp),
                    )
                } else if (!hasCameraPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = BrandyBrown.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Permiso de cámara no concedido",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("Habilitar Cámara", fontSize = 11.sp)
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = BrandyBrown.copy(alpha = 0.7f),
                        modifier = Modifier.size(72.dp),
                    )
                    // Indicador de escaneo activo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(2.dp)
                            .align(Alignment.TopCenter)
                            .padding(top = (laserOffset * 200).dp)
                            .background(BrandyBrown.copy(alpha = 0.8f))
                    )
                }
            }

            Text(
                text = "Apunta la cámara del dispositivo al código QR que muestra el cliente en su aplicación móvil.",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            // Entrada de token QR para pruebas y contingencia
            OutlinedTextField(
                value = manualTokenInput,
                onValueChange = { manualTokenInput = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Token QR (Pruebas / Fallback)") },
                supportingText = {
                    Text(
                        text = "Token de cliente (${manualTokenInput.length}/32-512 chars)",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                isError = manualTokenInput.isNotEmpty() && manualTokenInput.length !in 32..512,
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
                    if (manualTokenInput.length in 32..512 && isOnline && !isScanning) {
                        onQrScanned(manualTokenInput)
                    }
                },
                enabled = manualTokenInput.length in 32..512 && isOnline && !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Validando QR...")
                } else {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Identificar por Código QR")
                }
            }
        }
    }
}
