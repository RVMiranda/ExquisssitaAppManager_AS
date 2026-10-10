package com.mirandadevsource.exquisssitaappmanager.features.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession

private val VanillaCustard = Color(0xFFFAF3E0)
private val BrandyBrown = Color(0xFF7E392F)
private val ChocolateFondant = Color(0xFF3D2723)
private val ApricotIllusion = Color(0xFFE5C2A4)
private val OfflineAmber = Color(0xFFA07324)

@Composable
fun LoginScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Surface(modifier = modifier.fillMaxSize(), color = VanillaCustard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "La Exquisssita",
                style = MaterialTheme.typography.headlineMedium,
                color = BrandyBrown,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Iniciar sesión",
                style = MaterialTheme.typography.titleLarge,
                color = ChocolateFondant,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
                enabled = !state.isSubmitting,
                singleLine = true,
                label = { Text("Correo") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                enabled = !state.isSubmitting,
                singleLine = true,
                label = { Text("Contraseña") },
                visualTransformation = if (state.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = viewModel::togglePasswordVisibility,
                        enabled = !state.isSubmitting,
                    ) {
                        Icon(
                            imageVector = if (state.passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (state.passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = BrandyBrown,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                }),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(16.dp))

            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandyBrown,
                    contentColor = Color.White,
                    disabledContainerColor = BrandyBrown.copy(alpha = 0.6f),
                    disabledContentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    disabledElevation = 0.dp,
                ),
                border = BorderStroke(1.dp, BrandyBrown),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.isSubmitting) "Conectando…" else "Entrar")
            }
        }
    }
}

@Composable
fun AuthLoadingScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = VanillaCustard) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = BrandyBrown)
            Spacer(Modifier.height(16.dp))
            Text("Revisando sesión", color = ChocolateFondant)
        }
    }
}

@Composable
fun AuthenticatedHomeScreen(
    session: AppUserSession?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = VanillaCustard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Exquisssita Manager", style = MaterialTheme.typography.headlineSmall, color = BrandyBrown)
            Text("Sesión iniciada", style = MaterialTheme.typography.titleLarge, color = ChocolateFondant)
            Text(session?.employeeName.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = ChocolateFondant)
            Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ChocolateFondant)
            Text(session?.role?.displayName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ChocolateFondant)
            if (session?.isOffline == true) {
                Text("Sesión sin conexión · los datos guardados estarán disponibles al habilitar la sincronización", color = OfflineAmber)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            ) {
                Text("Cerrar sesión")
            }
            Text(
                "El acceso a datos operativos se habilitará por módulos.",
                color = ChocolateFondant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandyBrown,
    unfocusedBorderColor = ApricotIllusion,
    focusedLabelColor = BrandyBrown,
    unfocusedLabelColor = ChocolateFondant,
    cursorColor = BrandyBrown,
    focusedTextColor = ChocolateFondant,
    unfocusedTextColor = ChocolateFondant,
)
