package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpsertEmployeeRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeConflictSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val employeeJson = Json { ignoreUnknownKeys = true }

fun EmployeeDto.toDomain(): Employee = Employee(
    id = id,
    userId = userId,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salarioCents = salario,
    diaPago = diaPago,
    activo = activo,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
    syncState = EmployeeEntity.SYNCED,
)

fun Employee.toUpsertDto(): UpsertEmployeeRequestDto = UpsertEmployeeRequestDto(
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salario = salarioCents,
    diaPago = diaPago,
    activo = activo,
)

fun EmployeeEntity.toDomain(): Employee = Employee(
    id = localId,
    userId = userId,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salarioCents = salarioCents,
    diaPago = diaPago,
    activo = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
    syncState = syncState,
    syncError = lastSyncError,
    conflictSnapshot = conflictServerJson?.let { serialized ->
        runCatching {
            employeeJson.decodeFromString(EmployeeDto.serializer(), serialized).toConflictSnapshot()
        }.getOrNull()
    },
)

fun EmployeeDto.toConflictSnapshotJson(): String =
    employeeJson.encodeToString(EmployeeDto.serializer(), this)

private fun EmployeeDto.toConflictSnapshot() = EmployeeConflictSnapshot(
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salarioCents = salario,
    diaPago = diaPago,
    activo = activo,
    syncVersion = syncVersion,
    updatedAt = updatedAt.orEmpty(),
)

fun EmployeeDto.toEntity(
    localId: String = id,
    syncState: String = EmployeeEntity.SYNCED,
    cacheActorUserId: String? = null,
): EmployeeEntity = EmployeeEntity(
    localId = localId,
    remoteId = id,
    cacheActorUserId = cacheActorUserId,
    userId = userId,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salarioCents = salario,
    diaPago = diaPago,
    activo = activo,
    createdAt = createdAt.orEmpty(),
    updatedAt = updatedAt.orEmpty(),
    syncVersion = syncVersion,
    syncState = syncState,
    lastSyncedAtMillis = System.currentTimeMillis(),
)

fun EmployeeEntity.toUpsertDto(): UpsertEmployeeRequestDto = UpsertEmployeeRequestDto(
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    salario = salarioCents,
    diaPago = diaPago,
    activo = activo,
)
