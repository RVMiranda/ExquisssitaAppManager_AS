package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncExecutePayload(
    @SerialName("p_entity") val entity: String,
    @SerialName("p_entity_id") val entityId: String,
    @SerialName("p_operation") val operation: String,
    @SerialName("p_payload") val payload: JsonObject,
    @SerialName("p_key") val key: String,
    @SerialName("p_expected_version") val expectedVersion: Long,
)

@Serializable
data class SyncCommandResponse(
    val status: String = "confirmed",
    val reason: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    val version: Long? = null,
)

@Serializable
data class CancelDetailPayload(
    @SerialName("p_detalle_orden_id") val detailId: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
)
