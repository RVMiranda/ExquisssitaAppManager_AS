package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val nombre: String,
    val precio: Long,
    val categoria: String,
    @SerialName("controla_inventario") val controlaInventario: Boolean,
    @SerialName("stock_actual") val stockActual: Int? = null,
    @SerialName("stock_minimo") val stockMinimo: Int? = null,
    val activo: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)
