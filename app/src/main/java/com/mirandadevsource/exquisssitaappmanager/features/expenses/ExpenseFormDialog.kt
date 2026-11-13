package com.mirandadevsource.exquisssitaappmanager.features.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mirandadevsource.exquisssitaappmanager.core.utils.CurrencyFormatter
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseCategories
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpensePaymentMethod
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

@Composable
fun ExpenseFormDialog(
    initialExpense: Expense?,
    templates: List<ExpenseTemplate>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        concept: String,
        category: String,
        amountCents: Long,
        quantity: Double?,
        unitPriceCents: Long?,
        paymentMethod: ExpensePaymentMethod,
        templateId: String?,
        notes: String?,
    ) -> Unit,
) {
    var concept by remember(initialExpense) { mutableStateOf(initialExpense?.concept.orEmpty()) }
    var category by remember(initialExpense) { mutableStateOf(initialExpense?.category ?: ExpenseCategories.INSUMOS) }
    var paymentMethod by remember(initialExpense) {
        mutableStateOf(initialExpense?.paymentMethod ?: ExpensePaymentMethod.EFECTIVO)
    }
    var notes by remember(initialExpense) { mutableStateOf(initialExpense?.notes.orEmpty()) }
    var selectedTemplateId by remember(initialExpense) { mutableStateOf(initialExpense?.templateId) }

    // Modo de captura: Por cantidad x precio o directo
    val isUnitCalculationInitial = initialExpense?.quantity != null && initialExpense.unitPriceCents != null
    var isUnitCalculationMode by remember(initialExpense) { mutableStateOf(isUnitCalculationInitial) }

    var quantityText by remember(initialExpense) {
        mutableStateOf(initialExpense?.quantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var unitPriceText by remember(initialExpense) {
        mutableStateOf(initialExpense?.unitPriceCents?.let { (it / 100.0).toString() }.orEmpty())
    }
    var directAmountText by remember(initialExpense) {
        mutableStateOf(
            if (!isUnitCalculationInitial && initialExpense != null && initialExpense.amountCents > 0L) {
                (initialExpense.amountCents / 100.0).toString()
            } else ""
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    // Cálculo derivado de centavos
    val calculatedCents: Long = remember(isUnitCalculationMode, quantityText, unitPriceText, directAmountText) {
        if (isUnitCalculationMode) {
            val q = quantityText.toDoubleOrNull() ?: 0.0
            val pCents = CurrencyFormatter.parsePesosToCents(unitPriceText) ?: 0L
            if (q > 0.0 && pCents > 0L) (q * pCents).roundToLong() else 0L
        } else {
            CurrencyFormatter.parsePesosToCents(directAmountText) ?: 0L
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ApricotIllusion,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (initialExpense == null) "Registrar Gasto" else "Editar Gasto",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = "Egreso operativo del día",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandyBrown,
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = BrandyBrown,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Plantillas rápidas de insumos frecuentes
                if (templates.isNotEmpty()) {
                    Text(
                        text = "Gastos frecuentes / Plantillas:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ChocolateFondant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        templates.forEach { tmpl ->
                            val isSelected = selectedTemplateId == tmpl.localId
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTemplateId = tmpl.localId
                                    concept = tmpl.name
                                    category = tmpl.category
                                    if (tmpl.unitPriceCents > 0L) {
                                        isUnitCalculationMode = true
                                        unitPriceText = (tmpl.unitPriceCents / 100.0).toString()
                                    }
                                },
                                label = {
                                    val priceHint = if (tmpl.unitPriceCents > 0L) {
                                        " (${CurrencyFormatter.formatCents(tmpl.unitPriceCents)}/${tmpl.unitOfMeasure})"
                                    } else ""
                                    Text(
                                        text = "${tmpl.name}$priceHint",
                                        style = MaterialTheme.typography.bodySmall,
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
                                    borderColor = BrandyBrown.copy(alpha = 0.3f),
                                    selectedBorderColor = BrandyBrown,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Campo Concepto
                OutlinedTextField(
                    value = concept,
                    onValueChange = {
                        concept = it
                        validationError = null
                    },
                    label = { Text("Concepto del gasto *") },
                    placeholder = { Text("Ej. 10 kg tortilla, Reja Coca-Cola...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = BrandyBrown.copy(alpha = 0.4f),
                        focusedLabelColor = BrandyBrown,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selector de Categoría
                Text(
                    text = "Categoría:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExpenseCategories.ALL.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de modo de cálculo: Directo o Por Kilo/Unidad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !isUnitCalculationMode,
                        onClick = { isUnitCalculationMode = false },
                        label = { Text("Monto total") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandyBrown,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = ChocolateFondant,
                        ),
                    )
                    FilterChip(
                        selected = isUnitCalculationMode,
                        onClick = { isUnitCalculationMode = true },
                        label = { Text("Por cantidad y precio") },
                        modifier = Modifier.weight(1.3f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandyBrown,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = ChocolateFondant,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isUnitCalculationMode) {
                    // Modo Por Cantidad y Precio Unitario
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it
                                validationError = null
                            },
                            label = { Text("Cantidad (kg/pza) *") },
                            placeholder = { Text("Ej. 10") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandyBrown,
                                unfocusedBorderColor = BrandyBrown.copy(alpha = 0.4f),
                                focusedLabelColor = BrandyBrown,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )

                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = {
                                unitPriceText = it
                                validationError = null
                            },
                            label = { Text("Precio unitario ($) *") },
                            placeholder = { Text("Ej. 24.00") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandyBrown,
                                unfocusedBorderColor = BrandyBrown.copy(alpha = 0.4f),
                                focusedLabelColor = BrandyBrown,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                } else {
                    // Modo Monto Directo
                    OutlinedTextField(
                        value = directAmountText,
                        onValueChange = {
                            directAmountText = it
                            validationError = null
                        },
                        label = { Text("Monto total del gasto ($) *") },
                        placeholder = { Text("Ej. 290.00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandyBrown,
                            unfocusedBorderColor = BrandyBrown.copy(alpha = 0.4f),
                            focusedLabelColor = BrandyBrown,
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                }

                // Tarjeta de total calculado
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BrandyBrown.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total a registrar:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = ChocolateFondant,
                        )
                        Text(
                            text = CurrencyFormatter.formatCents(calculatedCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (calculatedCents > 0L) BrandyBrown else Color.Gray,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Método de pago
                Text(
                    text = "Método de pago:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ChocolateFondant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExpensePaymentMethod.values().forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method.displayName) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notas opcionales
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas o detalles adicionales") },
                    placeholder = { Text("Ej. Dinero puesto por el dueño, reposición...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandyBrown,
                        unfocusedBorderColor = BrandyBrown.copy(alpha = 0.4f),
                        focusedLabelColor = BrandyBrown,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Mensaje de error de validación
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (concept.isBlank()) {
                                validationError = "El concepto es obligatorio."
                                return@Button
                            }
                            if (calculatedCents <= 0L) {
                                validationError = "El monto debe ser mayor a cero."
                                return@Button
                            }

                            val q = if (isUnitCalculationMode) quantityText.toDoubleOrNull() else null
                            val pCents = if (isUnitCalculationMode) CurrencyFormatter.parsePesosToCents(unitPriceText) else null

                            onSave(
                                concept.trim(),
                                category.trim(),
                                calculatedCents,
                                q,
                                pCents,
                                paymentMethod,
                                selectedTemplateId,
                                notes.trim().ifBlank { null },
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                        ),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardando...")
                        } else {
                            Text(
                                text = if (initialExpense == null) "Registrar Gasto" else "Guardar Cambios",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
