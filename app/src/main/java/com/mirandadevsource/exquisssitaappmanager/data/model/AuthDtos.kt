package com.mirandadevsource.exquisssitaappmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserRoleDto(
    val role: String,
)

@Serializable
data class EmployeeAuthProfileDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val nombre: String,
    val apellido: String,
    val activo: Boolean = true,
)

@Serializable
data class CachedAuthMetadata(
    val userId: String,
    val email: String,
    val role: String,
    val employeeId: String,
    val employeeName: String,
    val startedAtEpochMillis: Long,
)
