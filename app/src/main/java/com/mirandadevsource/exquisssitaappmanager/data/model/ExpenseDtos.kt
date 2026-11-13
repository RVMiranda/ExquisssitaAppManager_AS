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
data class CreateExpenseTemplateRequestDto(
    val id: String,
    val nombre: String,
    val categoria: String,
    @SerialName("precio_unitario") val precioUnitario: Long,
    @SerialName("unidad_medida") val unidadMedida: String,
    val activo: Boolean = true,
)

@Serializable
data class UpdateExpenseTemplateRequestDto(
    val nombre: String,
    val categoria: String,
    @SerialName("precio_unitario") val precioUnitario: Long,
    @SerialName("unidad_medida") val unidadMedida: String,
    val activo: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class DailyProfitSummaryDto(
    @SerialName("fecha") val fecha: String = "",
    @SerialName("ventas_totales") val ventasTotales: Long = 0L,
    @SerialName("ventas_efectivo") val ventasEfectivo: Long = 0L,
    @SerialName("ventas_tarjeta") val ventasTarjeta: Long = 0L,
    @SerialName("ventas_otro") val ventasOtro: Long = 0L,
    @SerialName("num_ordenes_cobradas") val numOrdenesCobradas: Int = 0,
    @SerialName("gastos_totales") val gastosTotales: Long = 0L,
    @SerialName("gastos_efectivo") val gastosEfectivo: Long = 0L,
    @SerialName("gastos_otros_metodos") val gastosOtrosMetodos: Long = 0L,
    @SerialName("num_gastos_registrados") val numGastosRegistrados: Int = 0,
    @SerialName("nomina_total") val nominaTotal: Long = 0L,
    @SerialName("num_pagos_nomina") val numPagosNomina: Int = 0,
    @SerialName("egresos_totales") val egresosTotales: Long = 0L,
    @SerialName("ganancia_neta") val gananciaNeta: Long = 0L,
    @SerialName("efectivo_en_caja_esperado") val efectivoEnCajaEsperado: Long = 0L,
    @SerialName("ventas_totales_centavos") val ventasTotalesCentavosAlt: Long = 0L,
    @SerialName("ventas_efectivo_centavos") val ventasEfectivoCentavosAlt: Long = 0L,
    @SerialName("gastos_totales_centavos") val gastosTotalesCentavosAlt: Long = 0L,
    @SerialName("gastos_efectivo_centavos") val gastosEfectivoCentavosAlt: Long = 0L,
    @SerialName("nomina_total_centavos") val nominaTotalCentavosAlt: Long = 0L,
    @SerialName("egresos_totales_centavos") val egresosTotalesCentavosAlt: Long = 0L,
    @SerialName("ganancia_neta_centavos") val gananciaNetaCentavosAlt: Long = 0L,
    @SerialName("efectivo_en_caja_esperado_centavos") val efectivoEnCajaEsperadoCentavosAlt: Long = 0L,
) {
    val resolvedVentasTotales: Long
        get() = if (ventasTotales != 0L) ventasTotales else ventasTotalesCentavosAlt

    val resolvedVentasEfectivo: Long
        get() = if (ventasEfectivo != 0L) ventasEfectivo else ventasEfectivoCentavosAlt

    val resolvedGastosTotales: Long
        get() = if (gastosTotales != 0L) gastosTotales else gastosTotalesCentavosAlt

    val resolvedGastosEfectivo: Long
        get() = if (gastosEfectivo != 0L) gastosEfectivo else gastosEfectivoCentavosAlt

    val resolvedNominaTotal: Long
        get() = if (nominaTotal != 0L) nominaTotal else nominaTotalCentavosAlt

    val resolvedEgresosTotales: Long
        get() = if (egresosTotales != 0L) egresosTotales else (resolvedGastosTotales + resolvedNominaTotal)

    val resolvedGananciaNeta: Long
        get() = if (gananciaNeta != 0L) gananciaNeta else (resolvedVentasTotales - resolvedEgresosTotales)

    val resolvedEfectivoCajaEsperado: Long
        get() = if (efectivoEnCajaEsperado != 0L) efectivoEnCajaEsperado else (resolvedVentasEfectivo - resolvedGastosEfectivo - resolvedNominaTotal)
}

@Serializable
data class DailyProfitRpcParams(
    @SerialName("p_fecha") val fecha: String,
)
