package com.mirandadevsource.exquisssitaappmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmployeePaymentDto(
    val id: String,
    @SerialName("empleado_id") val empleadoId: String,
    val monto: Long,
    @SerialName("fecha_pago") val fechaPago: String,
    val notas: String? = null,
    @SerialName("periodo_inicio") val periodoInicio: String? = null,
    @SerialName("fecha_programada") val fechaProgramada: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L,
)

@Serializable
data class RegisterPayrollPaymentRpcParams(
    @SerialName("p_empleado_id") val empleadoId: String,
    @SerialName("p_periodo_inicio") val periodoInicio: String,
    @SerialName("p_fecha_pago") val fechaPago: String? = null,
    @SerialName("p_notas") val notas: String? = null,
)
