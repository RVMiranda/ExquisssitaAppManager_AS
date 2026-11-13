package com.mirandadevsource.exquisssitaappmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("fecha") val fecha: String,
    @SerialName("concepto") val concepto: String,
    @SerialName("categoria") val categoria: String = "Insumos",
    @SerialName("monto") val monto: Long,
    @SerialName("cantidad") val cantidad: Double? = null,
    @SerialName("precio_unitario") val precioUnitario: Long? = null,
    @SerialName("metodo_pago") val metodoPago: String = "Efectivo",
    @SerialName("plantilla_id") val plantillaId: String? = null,
    @SerialName("notas") val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L,
)

@Serializable
data class CreateExpenseRequestDto(
    val id: String,
    val fecha: String,
    val concepto: String,
    val categoria: String,
    val monto: Long,
    val cantidad: Double? = null,
    @SerialName("precio_unitario") val precioUnitario: Long? = null,
    @SerialName("metodo_pago") val metodoPago: String,
    @SerialName("plantilla_id") val plantillaId: String? = null,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
)

@Serializable
data class UpdateExpenseRequestDto(
    val fecha: String,
    val concepto: String,
    val categoria: String,
    val monto: Long,
    val cantidad: Double? = null,
    @SerialName("precio_unitario") val precioUnitario: Long? = null,
    @SerialName("metodo_pago") val metodoPago: String,
    @SerialName("plantilla_id") val plantillaId: String? = null,
    val notas: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ExpenseTemplateDto(
    val id: String,
    val nombre: String,
    val categoria: String = "Insumos",
    @SerialName("precio_unitario") val precioUnitario: Long = 0L,
    @SerialName("unidad_medida") val unidadMedida: String = "kg",
    val activo: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L,
)

@Serializable
data class DailyProfitSummaryDto(
    @SerialName("fecha") val fecha: String,
    @SerialName("ventas_totales_centavos") val ventasTotalesCentavos: Long = 0L,
    @SerialName("ventas_efectivo_centavos") val ventasEfectivoCentavos: Long = 0L,
    @SerialName("ventas_tarjeta_centavos") val ventasTarjetaCentavos: Long = 0L,
    @SerialName("ventas_otro_centavos") val ventasOtroCentavos: Long = 0L,
    @SerialName("num_ordenes_cobradas") val numOrdenesCobradas: Int = 0,
    @SerialName("gastos_totales_centavos") val gastosTotalesCentavos: Long = 0L,
    @SerialName("gastos_efectivo_centavos") val gastosEfectivoCentavos: Long = 0L,
    @SerialName("gastos_otros_metodos_centavos") val gastosOtrosMetodosCentavos: Long = 0L,
    @SerialName("num_gastos_registrados") val numGastosRegistrados: Int = 0,
    @SerialName("nomina_total_centavos") val nominaTotalCentavos: Long = 0L,
    @SerialName("num_pagos_nomina") val numPagosNomina: Int = 0,
    @SerialName("egresos_totales_centavos") val egresosTotalesCentavos: Long = 0L,
    @SerialName("ganancia_neta_centavos") val gananciaNetaCentavos: Long = 0L,
    @SerialName("efectivo_en_caja_esperado_centavos") val efectivoEnCajaEsperadoCentavos: Long = 0L,
)

@Serializable
data class DailyProfitRpcParams(
    @SerialName("p_fecha") val fecha: String,
)
