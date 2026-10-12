package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class RegisterPaymentPayload(
    @SerialName("p_orden_id") val orderId: String,
    @SerialName("p_monto") val amount: Long,
    @SerialName("p_metodo_pago") val method: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
    @SerialName("p_asignaciones") val allocations: JsonArray,
)
