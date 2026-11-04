package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeePaymentDto
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentSyncStatus

fun EmployeePaymentDto.toDomain(): EmployeePayment = EmployeePayment(
    id = id,
    empleadoId = empleadoId,
    montoCents = monto,
    fechaPago = fechaPago,
    periodoInicio = periodoInicio,
    fechaProgramada = fechaProgramada,
    notas = notas,
    registradoPor = registradoPor,
    createdAt = createdAt.orEmpty(),
    syncVersion = syncVersion,
    descuentoPrestamoCents = descuentoPrestamo,
    prestamoId = prestamoId,
)

fun PayrollRecordEntity.toDomain(): EmployeePayment = EmployeePayment(
    id = localId,
    empleadoId = employeeLocalId ?: empleadoIdRemote,
    montoCents = montoCents,
    fechaPago = fechaPago,
    periodoInicio = periodoInicio,
    fechaProgramada = fechaProgramada,
    notas = notas,
    registradoPor = registradoPor,
    createdAt = createdAt,
    syncVersion = syncVersion,
    syncStatus = PaymentSyncStatus.CONFIRMED,
)

fun EmployeePaymentDto.toEntity(employeeLocalId: String? = null, cacheActorUserId: String? = null): PayrollRecordEntity = PayrollRecordEntity(
    localId = cacheActorUserId?.let { "$it:$id" } ?: id,
    remoteId = id,
    cacheActorUserId = cacheActorUserId,
    employeeLocalId = employeeLocalId,
    empleadoIdRemote = empleadoId,
    montoCents = monto,
    fechaPago = fechaPago,
    notas = notas,
    periodoInicio = periodoInicio,
    fechaProgramada = fechaProgramada,
    registradoPor = registradoPor,
    createdAt = createdAt.orEmpty(),
    syncVersion = syncVersion,
)

fun PayrollPaymentIntentEntity.toDomain(employee: EmployeeEntity): EmployeePayment = EmployeePayment(
    id = intentId,
    empleadoId = employeeLocalId,
    montoCents = employee.salarioCents,
    fechaPago = requestedFechaPago,
    periodoInicio = periodoInicio,
    fechaProgramada = null,
    notas = notas,
    registradoPor = null,
    createdAt = createdAtMillis.toString(),
    syncStatus = PaymentSyncStatus.PENDING_SYNC,
)
