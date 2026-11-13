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
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseCategories
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ApricotIllusion
import com.mirandadevsource.exquisssitaappmanager.ui.theme.BrandyBrown
import com.mirandadevsource.exquisssitaappmanager.ui.theme.ChocolateFondant
import com.mirandadevsource.exquisssitaappmanager.ui.theme.VanillaCustard
import java.math.BigDecimal
import kotlin.math.roundToLong

private val PRESET_UNITS = listOf("kg", "pza", "reja", "costal", "litro", "servicio", "paquete")

@Composable
fun ExpenseTemplateFormDialog(
    initialTemplate: ExpenseTemplate?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        unitPriceCents: Long,
        unitOfMeasure: String,
        isActive: Boolean,
    ) -> Unit,
) {
    var name by remember(initialTemplate) { mutableStateOf(initialTemplate?.name.orEmpty()) }
    var category by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.category ?: ExpenseCategories.INSUMOS)
    }
    var unitOfMeasure by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.unitOfMeasure ?: "kg")
    }
    var unitPriceText by remember(initialTemplate) {
        mutableStateOf(
            initialTemplate?.let {
                if (it.unitPriceCents > 0L) (it.unitPriceCents / 100.0).toString() else ""
            }.orEmpty()
        )
    }
    var isActive by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.isActive ?: true)
    }

    val parsedUnitPriceCents: Long = remember(unitPriceText) {
        val clean = unitPriceText.trim().replace(",", ".")
        val bd = clean.toBigDecimalOrNull()
        if (bd != null && bd >= BigDecimal.ZERO) {
            (bd.toDouble() * 100.0).roundToLong()
        } else 0L
    }

    val isValid = name.isNotBlank()

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VanillaCustard,
            border = BorderStroke(1.dp, ApricotIllusion),
            tonalElevation = 6.dp,
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
                            text = if (initialTemplate == null) "Nueva Plantilla" else "Editar Plantilla",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandyBrown,
                        )
                        Text(
                            text = "Insumo o producto frecuente del negocio",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = ChocolateFondant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nombre libre del insumo
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto / Insumo *") },
                    placeholder = { Text("Ej. Tortilla de comal, Reja Coca-Cola") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = expenseDialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Categoría
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpenseCategories.ALL.forEach { cat ->
                        val selected = category.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandyBrown,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ApricotIllusion,
                                selectedBorderColor = BrandyBrown,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Unidad de Medida
                OutlinedTextField(
                    value = unitOfMeasure,
                    onValueChange = { unitOfMeasure = it },
                    label = { Text("Unidad de Medida") },
                    placeholder = { Text("Ej. kg, pza, reja, costal, litro") },
                    singleLine = true,
                    colors = expenseDialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PRESET_UNITS.forEach { unit ->
                        val selected = unitOfMeasure.equals(unit, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { unitOfMeasure = unit },
                            label = { Text(unit, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ApricotIllusion,
                                selectedLabelColor = ChocolateFondant,
                                containerColor = Color.White,
                                labelColor = ChocolateFondant.copy(alpha = 0.8f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ApricotIllusion,
                                selectedBorderColor = BrandyBrown,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Precio Unitario Sugerido (opcional o pactado)
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text("Precio Unitario Pactado / Base ($ MXN)") },
                    placeholder = { Text("Ej. 24.00 (dejar vacío si es variable)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = expenseDialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    supportingText = {
                        Text(
                            text = if (parsedUnitPriceCents > 0L) {
                                "Precio base sugerido: ${CurrencyFormatter.formatCents(parsedUnitPriceCents)} por $unitOfMeasure"
                            } else {
                                "Precio libre: se definirá en cada compra"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ChocolateFondant.copy(alpha = 0.7f),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Switch de Activo
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ApricotIllusion),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Activo en catálogo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ChocolateFondant,
                            )
                            Text(
                                text = "Disponible para selección rápida al registrar gastos",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChocolateFondant.copy(alpha = 0.6f),
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandyBrown,
                                uncheckedThumbColor = ChocolateFondant.copy(alpha = 0.5f),
                                uncheckedTrackColor = ApricotIllusion,
                            ),
                            enabled = !isSaving,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones Guardar / Cancelar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BrandyBrown),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandyBrown),
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                category.trim(),
                                parsedUnitPriceCents,
                                unitOfMeasure.trim().ifBlank { "pza" },
                                isActive,
                            )
                        },
                        enabled = isValid && !isSaving,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandyBrown,
                            contentColor = Color.White,
                            disabledContainerColor = BrandyBrown.copy(alpha = 0.4f),
                        ),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
fun expenseDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandyBrown,
    unfocusedBorderColor = ApricotIllusion,
    focusedLabelColor = BrandyBrown,
    unfocusedLabelColor = ChocolateFondant.copy(alpha = 0.7f),
    cursorColor = BrandyBrown,
    focusedTextColor = ChocolateFondant,
    unfocusedTextColor = ChocolateFondant,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
)
