package com.mirandadevsource.exquisssitaappmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val nombre: String,
    val telefono: String? = null,
    @SerialName("visitas_totales") val visitasTotales: Int? = null,
    @SerialName("puntos_lealtad") val puntosLealtad: Int? = null,
    @SerialName("fecha_registro") val fechaRegistro: String? = null,
    @SerialName("sync_version") val syncVersion: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CustomerVisitDto(
    val registered: Boolean,
    @SerialName("already_registered") val alreadyRegistered: Boolean,
    @SerialName("cliente_id") val clienteId: String,
    val fecha: String,
    @SerialName("puntos_otorgados") val puntosOtorgados: Int? = null,
    @SerialName("visitas_totales") val visitasTotales: Int? = null,
    @SerialName("puntos_lealtad") val puntosLealtad: Int? = null,
)

@Serializable
data class RegisterVisitRequestDto(
    @SerialName("p_qr_token") val qrToken: String,
)

@Serializable
data class LoyaltyRewardDto(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerialName("costo_puntos") val pointsCost: Int,
    val activo: Boolean,
    @SerialName("sync_version") val syncVersion: Long,
)

@Serializable
data class CustomerRedeemedRewardDto(
    @SerialName("recompensa_id") val rewardId: String,
)

@Serializable
data class ManageLoyaltyRewardRequestDto(
    @SerialName("p_id") val id: String,
    @SerialName("p_titulo") val title: String,
    @SerialName("p_descripcion") val description: String,
    @SerialName("p_costo_puntos") val pointsCost: Int,
    @SerialName("p_activo") val active: Boolean,
    @SerialName("p_expected_version") val expectedVersion: Long,
)

@Serializable
data class RedeemLoyaltyRewardRequestDto(
    @SerialName("p_qr_token") val qrToken: String,
    @SerialName("p_recompensa_id") val rewardId: String,
    @SerialName("p_idempotency_key") val idempotencyKey: String,
)

@Serializable
data class RewardRedemptionDto(
    val status: String,
    @SerialName("canje_id") val redemptionId: String,
    @SerialName("cliente_id") val customerId: String,
    @SerialName("recompensa_id") val rewardId: String,
    @SerialName("puntos_costo") val pointsCost: Int,
    @SerialName("already_processed") val alreadyProcessed: Boolean,
    @SerialName("puntos_restantes") val remainingPoints: Int? = null,
)
