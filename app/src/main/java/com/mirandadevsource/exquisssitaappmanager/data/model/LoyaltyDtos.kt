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
    @SerialName("qr_token_hash") val qrTokenHash: String? = null,
    @SerialName("sync_version") val syncVersion: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CreateCustomerRequestDto(
    val nombre: String,
    val telefono: String? = null,
    @SerialName("qr_token_hash") val qrTokenHash: String,
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
