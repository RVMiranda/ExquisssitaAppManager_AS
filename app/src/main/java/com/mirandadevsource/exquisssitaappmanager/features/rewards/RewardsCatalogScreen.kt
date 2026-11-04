package com.mirandadevsource.exquisssitaappmanager.features.rewards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsCatalogScreen(
    viewModel: RewardsCatalogViewModel,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Auto-dismiss para notificaciones de éxito de 3 segundos
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            delay(3000)
            viewModel.dismissNotification()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VanillaCustard),
    ) {
        // Barra Superior
        Surface(
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    Column {
                        Text(
                            text = "Catálogo de Recompensas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                        Text(
                            text = if (uiState.canManageRewards) "Administración de Recompensas" else "Consulta de Recompensas",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Actualizar catálogo",
                            tint = BrandyBrown,
                        )
                    }

                    if (uiState.canManageRewards) {
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { viewModel.openCreateDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Nueva",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Aviso Offline
        if (!uiState.isOnline) {
            Surface(
                color = OfflineAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, OfflineAmber),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = OfflineAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sin conexión a internet. La gestión de recompensas requiere red activa.",
                        color = OfflineAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Notificación de Éxito
        uiState.successMessage?.let { msg ->
            Surface(
                color = BrandyBrown.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BrandyBrown),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = BrandyBrown,
                        fontSize = 12.sp,
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
                            tint = BrandyBrown,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Notificación de Error
        uiState.errorMessage?.let { err ->
            Surface(
                color = Color(0xFFB3261E).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFFB3261E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFB3261E),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = err,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.dismissNotification() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing || (uiState.isLoading && uiState.rewards.isNotEmpty()),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // Buscador
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Buscar por título o descripción...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Buscar",
                            tint = BrandyBrown,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = "Limpiar búsqueda",
                                    tint = ChocolateFondant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                        focusedContainerColor = Color.White.copy(alpha = 0.6f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // Pastillas de Filtro (FilterChips homologadas con Promociones)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RewardFilter.entries.forEach { filter ->
                        val isSelected = uiState.selectedFilter == filter
                        val count = when (filter) {
                            RewardFilter.TODAS -> uiState.totalCount
                            RewardFilter.ACTIVAS -> uiState.activeCount
                            RewardFilter.INACTIVAS -> uiState.inactiveCount
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onFilterSelected(filter) },
                            label = {
                                Text(
                                    text = "${filter.label} ($count)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = VanillaCustard,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = ApricotIllusion,
                                selectedBorderColor = BrandyBrown,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

            // Lista de Recompensas
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading && uiState.rewards.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(color = BrandyBrown, strokeWidth = 2.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Cargando catálogo...",
                                color = ChocolateFondant.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    uiState.filteredRewards.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = ChocolateFondant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "No se encontraron recompensas que coincidan con '${uiState.searchQuery}'."
                                } else {
                                    "No hay recompensas registradas en esta sección."
                                },
                                color = ChocolateFondant.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.filteredRewards, key = { it.id }) { reward ->
                                RewardCard(
                                    reward = reward,
                                    canManage = uiState.canManageRewards,
                                    onEdit = { viewModel.openEditDialog(reward) },
                                    onToggleActive = { viewModel.requestToggleActive(reward) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Modal de Creación / Edición
    if (uiState.isFormDialogOpen) {
        RewardFormDialog(
            isEditing = uiState.editingReward != null,
            title = uiState.formTitle,
            description = uiState.formDescription,
            pointsCost = uiState.formPointsCost,
            isActive = uiState.formIsActive,
            formError = uiState.formError,
            isSubmitting = uiState.isSubmitting,
            onTitleChange = viewModel::onFormTitleChange,
            onDescriptionChange = viewModel::onFormDescriptionChange,
            onPointsCostChange = viewModel::onFormPointsCostChange,
            onIsActiveChange = viewModel::onFormIsActiveChange,
            onSave = viewModel::saveReward,
            onDismiss = viewModel::dismissFormDialog,
        )
    }

    // Diálogo de Confirmación de Activación / Desactivación
    uiState.togglingReward?.let { reward ->
        ConfirmToggleActiveDialog(
            reward = reward,
            isSubmitting = uiState.isSubmitting,
            onConfirm = viewModel::confirmToggleActive,
            onDismiss = viewModel::dismissToggleActive,
        )
    }
}

@Composable
private fun RewardCard(
    reward: LoyaltyReward,
    canManage: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (reward.active) BrandyBrown else ApricotIllusion

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Fila de Cabecera: Título y Badges de Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reward.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Badge de Estado
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (reward.active) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrandyBrown.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = "Activa",
                                color = BrandyBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ChocolateFondant.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, ChocolateFondant.copy(alpha = 0.2f)),
                        ) {
                            Text(
                                text = "Inactiva",
                                color = ChocolateFondant.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (reward.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reward.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila Inferior: Costo en Puntos y Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Costo en Puntos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CardGiftcard,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Costo: ${reward.pointsCost} pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Acciones: Editar y Activar/Pausar
                if (canManage) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, BrandyBrown),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Editar",
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Editar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedButton(
                            onClick = onToggleActive,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (reward.active) OfflineAmber else BrandyBrown,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (reward.active) OfflineAmber else BrandyBrown,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(
                                imageVector = if (reward.active) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                                contentDescription = if (reward.active) "Pausar" else "Activar",
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (reward.active) "Desactivar" else "Activar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardFormDialog(
    isEditing: Boolean,
    title: String,
    description: String,
    pointsCost: String,
    isActive: Boolean,
    formError: String?,
    isSubmitting: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPointsCostChange: (String) -> Unit,
    onIsActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEditing) "Editar Recompensa" else "Nueva Recompensa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                    IconButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Campo Título
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Título (obligatorio)", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                        focusedContainerColor = Color.White.copy(alpha = 0.8f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.8f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // Campo Costo en Puntos
                OutlinedTextField(
                    value = pointsCost,
                    onValueChange = onPointsCostChange,
                    label = { Text("Costo en Puntos (obligatorio)", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                        focusedContainerColor = Color.White.copy(alpha = 0.8f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.8f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // Campo Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Descripción (opcional)", fontSize = 12.sp) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                        focusedTextColor = ChocolateFondant,
                        unfocusedTextColor = ChocolateFondant,
                        focusedContainerColor = Color.White.copy(alpha = 0.8f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.8f),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // Switch Activo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Disponible en mostrador",
                            fontWeight = FontWeight.SemiBold,
                            color = ChocolateFondant,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = if (isActive) "Los clientes pueden canjearla" else "Recompensa pausada / no disponible",
                            color = ChocolateFondant.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = onIsActiveChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandyBrown,
                            uncheckedThumbColor = ChocolateFondant.copy(alpha = 0.6f),
                            uncheckedTrackColor = ApricotIllusion.copy(alpha = 0.5f),
                        ),
                    )
                }

                // Error del Formulario
                if (formError != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFB3261E).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFB3261E)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFB3261E),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = formError,
                                color = Color(0xFFB3261E),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Botones Cancelar y Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ApricotIllusion),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChocolateFondant),
                    ) {
                        Text("Cancelar", fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onSave,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                        shape = RoundedCornerShape(6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guardando...", fontSize = 13.sp)
                        } else {
                            Text(if (isEditing) "Guardar Cambios" else "Crear Recompensa", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmToggleActiveDialog(
    reward: LoyaltyReward,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val willBeActive = !reward.active
    val actionVerb = if (willBeActive) "activar" else "desactivar"

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = if (willBeActive) "Activar Recompensa" else "Desactivar Recompensa",
                color = BrandyBrown,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "¿Confirmas que deseas $actionVerb la recompensa '${reward.title}'? ${
                    if (willBeActive) "Volverá a estar disponible para canje en mostrador."
                    else "Dejará de aparecer disponible para canje de los clientes."
                }",
                color = ChocolateFondant,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BrandyBrown),
                shape = RoundedCornerShape(6.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (willBeActive) "Activar" else "Desactivar")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, ApricotIllusion),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ChocolateFondant),
            ) {
                Text("Cancelar")
            }
        },
        containerColor = VanillaCustard,
        shape = RoundedCornerShape(12.dp),
    )
}
