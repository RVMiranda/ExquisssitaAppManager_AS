package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpsertEmployeeRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee

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
)

fun EmployeeDto.toEntity(
    localId: String = id,
    syncState: String = EmployeeEntity.SYNCED,
): EmployeeEntity = EmployeeEntity(
    localId = localId,
    remoteId = id,
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
