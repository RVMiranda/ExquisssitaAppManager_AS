package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderDetailDto(
    val id: String,
    @SerialName("orden_id") val ordenId: String,
    @SerialName("producto_id") val productoId: String,
    val cantidad: Int,
    @SerialName("precio_unitario") val precioUnitarioCentavos: Long,
    @SerialName("estado_pago") val estadoPago: String,
    val notas: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)
