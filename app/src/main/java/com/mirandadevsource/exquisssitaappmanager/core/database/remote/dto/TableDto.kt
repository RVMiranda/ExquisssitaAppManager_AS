package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TableDto(
    val id: String,
    @SerialName("numero_mesa") val numeroMesa: String,
    val estado: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sync_version") val syncVersion: Long,
)
