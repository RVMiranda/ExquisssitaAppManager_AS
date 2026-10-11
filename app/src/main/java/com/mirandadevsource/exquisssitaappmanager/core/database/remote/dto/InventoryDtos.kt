package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockMovementDto(
    val id: String,
    @SerialName("producto_id") val productId: String,
    val cantidad: Int,
    val tipo: String,
    @SerialName("referencia_id") val referenceId: String? = null,
    val notas: String? = null,
    @SerialName("creado_por") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("sync_version") val syncVersion: Long,
)

@Serializable
data class InventoryRpcSixParams(
    @SerialName("p_producto_id") val productId: String,
    @SerialName("p_cantidad") val quantity: Int,
    @SerialName("p_tipo") val type: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
    @SerialName("p_referencia_id") val referenceId: String?,
    @SerialName("p_notas") val notes: String?,
)

@Serializable
data class InventoryRpcVersionedParams(
    @SerialName("p_producto_id") val productId: String,
    @SerialName("p_cantidad") val quantity: Int,
    @SerialName("p_tipo") val type: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
    @SerialName("p_referencia_id") val referenceId: String?,
    @SerialName("p_notas") val notes: String?,
    @SerialName("p_expected_version") val expectedVersion: Long,
)

@Serializable
data class InventoryRpcResponseDto(
    val status: String,
    @SerialName("movement_id") val movementId: String? = null,
    @SerialName("product_version") val productVersion: Long? = null,
    val reason: String? = null,
)
