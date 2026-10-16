package com.mirandadevsource.exquisssitaappmanager.features.promotions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ErrorRed
import com.mirandadevsource.exquisssitaappmanager.ui.theme.OfflineAmber
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsScreen(
    viewModel: PromotionsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VanillaCustard),
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshPromotions() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Header con título, botón de refrescar y botón de Nueva Promoción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Promociones y Ofertas",
                            style = MaterialTheme.typography.headlineMedium,
                            color = BrandyBrown,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Catálogo de promociones, descuentos y vigencias",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.refreshPromotions() },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Actualizar promociones",
                                tint = BrandyBrown,
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        OutlinedButton(
                            onClick = { viewModel.openCreateDialog() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = BrandyBrown,
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, BrandyBrown),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Nueva",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Banner Offline
                if (!uiState.isOnline) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OfflineWarningBanner(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de Búsqueda de Ancho Completo
                PromotionsSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Banner de Métricas (KPIs)
                PromotionsKpiBanner(
                    total = uiState.totalCount,
                    active = uiState.activeCount,
                    inactive = uiState.inactiveCount,
                    expired = uiState.expiredCount,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pastillas de Filtrado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PromotionFilter.entries.forEach { filter ->
                        val isSelected = uiState.selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onFilterSelect(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = ApricotIllusion,
                                selectedBorderColor = BrandyBrown,
                                borderWidth = 1.dp,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido: Loading, Vacío o Lista
                if (uiState.isLoading && uiState.promotions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = BrandyBrown,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                } else if (uiState.filteredPromotions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyPromotionsView(
                            hasSearch = uiState.searchQuery.isNotBlank() || uiState.selectedFilter != PromotionFilter.TODAS,
                            onClearFilters = {
                                viewModel.onSearchQueryChange("")
                                viewModel.onFilterSelect(PromotionFilter.TODAS)
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(
                            items = uiState.filteredPromotions,
                            key = { it.id },
                        ) { promo ->
                            PromotionCard(
                                promotion = promo,
                                isExpired = uiState.isExpired(promo),
                                onEdit = { viewModel.openEditDialog(promo) },
                                onToggleActive = { viewModel.requestToggleActive(promo) },
                                onViewDetails = { viewModel.openDetailDialog(promo) },
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        // Diálogo de Formulario (Crear / Editar)
        if (uiState.isFormDialogOpen) {
            PromotionFormDialog(
                uiState = uiState,
                onDismiss = viewModel::dismissFormDialog,
                onTitleChange = viewModel::onFormTitleChange,
                onDescriptionChange = viewModel::onFormDescriptionChange,
                onTermsChange = viewModel::onFormTermsChange,
                onImageUrlChange = viewModel::onFormImageUrlChange,
                onExpirationDateChange = viewModel::onFormExpirationDateChange,
                onIsActiveChange = viewModel::onFormIsActiveChange,
                onSubmit = viewModel::submitForm,
            )
        }

        // Diálogo de Confirmación de Baja Operativa (Pausar)
        uiState.deactivatingPromotion?.let { promo ->
            DeactivatePromotionDialog(
                promotion = promo,
                isSubmitting = uiState.isSubmitting,
                onConfirm = viewModel::confirmDeactivation,
                onDismiss = viewModel::dismissDeactivateDialog,
            )
        }

        // Diálogo de Detalle y Términos y Condiciones
        uiState.viewingDetailPromotion?.let { promo ->
            PromotionDetailDialog(
                promotion = promo,
                isExpired = uiState.isExpired(promo),
                onDismiss = viewModel::dismissDetailDialog,
            )
        }
    }
}

@Composable
private fun PromotionsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Buscar promociones...",
                style = MaterialTheme.typography.bodyMedium,
                color = ChocolateFondant.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = BrandyBrown,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Limpiar búsqueda",
                        tint = BrandyBrown,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = BrandyBrown,
            unfocusedBorderColor = ApricotIllusion,
            focusedTextColor = ChocolateFondant,
            unfocusedTextColor = ChocolateFondant,
        ),
        modifier = modifier,
    )
}

@Composable
private fun OfflineWarningBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OfflineAmber.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, OfflineAmber),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = OfflineAmber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Modo sin conexión: visualizando catálogo en caché. Para crear o editar promociones se requiere internet.",
                style = MaterialTheme.typography.bodySmall,
                color = ChocolateFondant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PromotionsKpiBanner(
    total: Int,
    active: Int,
    inactive: Int,
    expired: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        border = BorderStroke(1.dp, ApricotIllusion),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KpiItem(title = "Total", value = total.toString(), color = ChocolateFondant)
            KpiItem(title = "Activas", value = active.toString(), color = BrandyBrown)
            KpiItem(title = "Inactivas", value = inactive.toString(), color = ChocolateFondant.copy(alpha = 0.6f))
            KpiItem(title = "Vencidas", value = expired.toString(), color = OfflineAmber)
        }
    }
}

@Composable
private fun KpiItem(
    title: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = ChocolateFondant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PromotionCard(
    promotion: Promotion,
    isExpired: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (promotion.activo && !isExpired) BrandyBrown else ApricotIllusion

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
                        text = promotion.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Badge de Estado
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isExpired) {
                        PromotionBadge(
                            text = "Vencida",
                            backgroundColor = OfflineAmber.copy(alpha = 0.15f),
                            textColor = OfflineAmber,
                        )
                    } else if (promotion.activo) {
                        PromotionBadge(
                            text = "Activa",
                            backgroundColor = BrandyBrown.copy(alpha = 0.12f),
                            textColor = BrandyBrown,
                        )
                    } else {
                        PromotionBadge(
                            text = "Inactiva",
                            backgroundColor = ChocolateFondant.copy(alpha = 0.08f),
                            textColor = ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Descripción
            Text(
                text = promotion.descripcion,
                style = MaterialTheme.typography.bodyMedium,
                color = ChocolateFondant.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Términos y Condiciones (si existen)
            if (!promotion.terminosCondiciones.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clickable { onViewDetails() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = BrandyBrown,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ver términos y condiciones",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandyBrown,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila Inferior: Vigencia y Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Fecha de Vigencia
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpired) Icons.Outlined.EventBusy else Icons.Outlined.Event,
                        contentDescription = null,
                        tint = if (isExpired) OfflineAmber else ChocolateFondant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (promotion.fechaVencimiento != null) {
                            "Vence: ${promotion.fechaVencimiento}"
                        } else {
                            "Permanente"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isExpired) OfflineAmber else ChocolateFondant.copy(alpha = 0.7f),
                        fontWeight = if (isExpired) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                // Acciones: Editar y Activar/Pausar
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
                            if (promotion.activo) OfflineAmber else BrandyBrown,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (promotion.activo) OfflineAmber else BrandyBrown,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(
                            imageVector = if (promotion.activo) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            contentDescription = if (promotion.activo) "Pausar" else "Activar",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (promotion.activo) "Pausar" else "Activar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyPromotionsView(
    hasSearch: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Campaign,
            contentDescription = null,
            tint = ChocolateFondant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (hasSearch) "No se encontraron promociones" else "No hay promociones registradas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ChocolateFondant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasSearch) {
                "Intenta cambiar el término de búsqueda o el filtro seleccionado."
            } else {
                "Pulsa '+ Nueva' para registrar tu primera promoción del negocio."
            },
            style = MaterialTheme.typography.bodySmall,
            color = ChocolateFondant.copy(alpha = 0.7f),
        )
        if (hasSearch) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClearFilters,
                border = BorderStroke(1.dp, BrandyBrown),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("Limpiar filtros", color = BrandyBrown)
            }
        }
    }
}

@Composable
private fun PromotionFormDialog(
    uiState: PromotionsUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTermsChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    onExpirationDateChange: (String) -> Unit,
    onIsActiveChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val isEditing = uiState.editingPromotion != null
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val initialMillis = remember(uiState.formExpirationDate) {
            try {
                if (uiState.formExpirationDate.isNotBlank()) {
                    val parts = uiState.formExpirationDate.split("-")
                    if (parts.size == 3) {
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.clear()
                        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        cal.timeInMillis
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            val year = cal.get(Calendar.YEAR)
                            val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                            val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                            onExpirationDateChange("$year-$month-$day")
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = "Aceptar",
                        color = BrandyBrown,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(
                        text = "Cancelar",
                        color = ChocolateFondant.copy(alpha = 0.7f),
                    )
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
            ),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = BrandyBrown,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = BrandyBrown,
                    todayContentColor = BrandyBrown,
                ),
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BrandyBrown),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Cabecera del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEditing) "Editar Promoción" else "Nueva Promoción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mensaje de Error en Formulario
                uiState.formError?.let { error ->
                    Surface(
                        color = ErrorRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ErrorRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = error,
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Título
                Text(
                    text = "Título *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.formTitle,
                    onValueChange = onTitleChange,
                    placeholder = { Text("Ej. 2x1 en Refrescos los Jueves") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Descripción
                Text(
                    text = "Descripción *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.formDescription,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Detalle de lo que incluye la promoción para el cliente...") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Términos y Condiciones (Nuevo Campo)
                Text(
                    text = "Términos y Condiciones (Opcional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.formTerms,
                    onValueChange = onTermsChange,
                    placeholder = { Text("Ej. Solo válida en consumo en sucursal. No acumulable.") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fecha de Vencimiento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Fecha de Vencimiento (Opcional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                    TextButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = BrandyBrown,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Elegir en calendario",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandyBrown,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.formExpirationDate,
                    onValueChange = onExpirationDateChange,
                    placeholder = { Text("AAAA-MM-DD (ej. 2026-12-31)") },
                    singleLine = true,
                    leadingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Event,
                                contentDescription = "Seleccionar fecha del calendario",
                                tint = BrandyBrown,
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.formExpirationDate.isNotBlank()) {
                                IconButton(onClick = { onExpirationDateChange("") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Borrar fecha",
                                        tint = ChocolateFondant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "Abrir calendario",
                                    tint = BrandyBrown,
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // URL de Imagen (Opcional)
                Text(
                    text = "URL de Imagen (Opcional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.formImageUrl,
                    onValueChange = onImageUrlChange,
                    placeholder = { Text("https://ejemplo.com/promo.png") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = BrandyBrown,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = ApricotIllusion,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Estado Activo (Al editar)
                if (isEditing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Promoción Activa",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                        Switch(
                            checked = uiState.formIsActive,
                            onCheckedChange = onIsActiveChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandyBrown,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ChocolateFondant.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones de Acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Cancelar", color = ChocolateFondant)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onSubmit,
                        enabled = !uiState.isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isEditing) "Guardar Cambios" else "Crear Promoción",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeactivatePromotionDialog(
    promotion: Promotion,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, OfflineAmber),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.PauseCircle,
                        contentDescription = null,
                        tint = OfflineAmber,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pausar Promoción",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChocolateFondant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "¿Deseas pausar la promoción \"${promotion.titulo}\"? Se mantendrá en el catálogo gerencial pero ya no estará visible para el público.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant.copy(alpha = 0.85f),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, ApricotIllusion),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Cancelar", color = ChocolateFondant)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OfflineAmber,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Pausar Promoción", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionDetailDialog(
    promotion: Promotion,
    isExpired: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BrandyBrown),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Detalle de Promoción",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandyBrown,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = promotion.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ChocolateFondant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = promotion.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChocolateFondant.copy(alpha = 0.85f),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sección Términos y Condiciones
                Surface(
                    color = VanillaCustard.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = BrandyBrown,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Términos y Condiciones",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandyBrown,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (!promotion.terminosCondiciones.isNullOrBlank()) {
                                promotion.terminosCondiciones
                            } else {
                                "Sin términos específicos registrados para esta promoción."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fechas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Vigencia",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = promotion.fechaVencimiento ?: "Permanente",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExpired) OfflineAmber else ChocolateFondant,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Estado",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = if (isExpired) "Vencida" else if (promotion.activo) "Activa" else "Inactiva",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExpired) OfflineAmber else if (promotion.activo) BrandyBrown else ChocolateFondant.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandyBrown,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
