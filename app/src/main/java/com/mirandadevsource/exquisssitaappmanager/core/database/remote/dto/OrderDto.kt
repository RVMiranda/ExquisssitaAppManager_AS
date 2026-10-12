package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: String,
    @SerialName("mesa_id") val mesaId: String? = null,
    @SerialName("cliente_id") val clienteId: String? = null,
    @SerialName("tipo_servicio") val tipoServicio: String,
    val estado: String,
    @SerialName("fecha_apertura") val fechaApertura: String,
    @SerialName("fecha_cierre") val fechaCierre: String? = null,
    val notas: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)
