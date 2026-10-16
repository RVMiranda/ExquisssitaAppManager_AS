package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromotionDto(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerialName("terminos_condiciones") val terminosCondiciones: String? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    @SerialName("fecha_publicacion") val fechaPublicacion: String,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val activo: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)

@Serializable
data class CreatePromotionRequestDto(
    val id: String,
    val titulo: String,
    val descripcion: String,
    @SerialName("terminos_condiciones") val terminosCondiciones: String? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val activo: Boolean = true,
)

@Serializable
data class UpdatePromotionRequestDto(
    val titulo: String,
    val descripcion: String,
    @SerialName("terminos_condiciones") val terminosCondiciones: String? = null,
    @SerialName("imagen_url") val imagenUrl: String? = null,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val activo: Boolean,
)

@Serializable
data class UpdatePromotionActiveDto(
    val activo: Boolean,
)
