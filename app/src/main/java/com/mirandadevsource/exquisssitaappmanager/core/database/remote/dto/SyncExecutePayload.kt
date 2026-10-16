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
    val id: String? = null,
    val fecha: String? = null,
    @SerialName("numero_corte") val numeroCorte: Int? = null,
    @SerialName("periodo_inicio") val periodoInicio: String? = null,
    @SerialName("periodo_fin") val periodoFin: String? = null,
    @SerialName("total_efectivo") val totalEfectivo: Long? = null,
    @SerialName("total_tarjeta") val totalTarjeta: Long? = null,
    @SerialName("total_otro") val totalOtro: Long? = null,
    @SerialName("total_global") val totalGlobal: Long? = null,
    @SerialName("num_ordenes") val numOrdenes: Int? = null,
    @SerialName("hora_cierre") val horaCierre: String? = null,
    @SerialName("cerrado_por") val cerradoPor: String? = null,
    @SerialName("sync_version") val syncVersion: Long? = null,
)

@Serializable
data class CancelDetailPayload(
    @SerialName("p_detalle_orden_id") val detailId: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
)
