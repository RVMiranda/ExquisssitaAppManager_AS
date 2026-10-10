package com.mirandadevsource.exquisssitaappmanager.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmployeeDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val nombre: String,
    val apellido: String,
    val telefono: String? = null,
    val salario: Long,
    @SerialName("dia_pago") val diaPago: Int,
    val activo: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L,
)

/** Does not expose id or user_id: the database generates ids and the app cannot link Auth. */
@Serializable
data class UpsertEmployeeRequestDto(
    val nombre: String,
    val apellido: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val telefono: String? = null,
    val salario: Long,
    @SerialName("dia_pago") val diaPago: Int,
    val activo: Boolean,
)

@Serializable
data class EmployeeActiveRequestDto(val activo: Boolean)
