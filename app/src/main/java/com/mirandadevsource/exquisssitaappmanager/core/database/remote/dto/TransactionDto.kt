package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("orden_id") val ordenId: String,
    val monto: Long,
    @SerialName("metodo_pago") val metodoPago: String,
    val fecha: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)

@Serializable
data class PaymentDetailDto(
    val id: String,
    @SerialName("transaccion_id") val transaccionId: String,
    @SerialName("detalle_orden_id") val detalleOrdenId: String,
    val cantidad: Int,
    val monto: Long,
)

@Serializable
data class PaymentStockDto(
    val id: String,
    @SerialName("transaccion_id") val transaccionId: String,
    @SerialName("detalle_orden_id") val detalleOrdenId: String,
    @SerialName("producto_id") val productoId: String,
    val cantidad: Int,
    @SerialName("movimiento_id") val movimientoId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("created_at") val createdAt: String,
)
