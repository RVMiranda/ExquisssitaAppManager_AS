package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.InventoryCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.StockMovementEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.StockMovementDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommandStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement

fun StockMovementDto.toEntity(): StockMovementEntity = StockMovementEntity(
    remoteId = id,
    productId = productId,
    quantity = cantidad,
    type = tipo,
    referenceId = referenceId,
    notes = notas,
    createdBy = createdBy,
    createdAt = createdAt,
    idempotencyKey = idempotencyKey,
    syncVersion = syncVersion,
)

fun StockMovementEntity.toDomain(): StockMovement = StockMovement(
    id = remoteId,
    productId = productId,
    quantity = quantity,
    type = InventoryMovementType.fromDatabase(type),
    referenceId = referenceId,
    notes = notes,
    createdBy = createdBy,
    createdAt = createdAt,
    idempotencyKey = idempotencyKey,
    syncVersion = syncVersion,
)

fun RegisterInventoryMovement.toEntity(
    actorUserId: String,
    idempotencyKey: String,
    createdAtMillis: Long = System.currentTimeMillis(),
): InventoryCommandEntity = InventoryCommandEntity(
    idempotencyKey = idempotencyKey,
    actorUserId = actorUserId,
    productId = productId,
    quantity = quantity,
    type = type.databaseValue,
    referenceId = referenceId,
    notes = notes,
    expectedVersion = expectedVersion,
    status = InventoryCommandStatus.PENDING.name,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = createdAtMillis,
)

fun InventoryCommandEntity.toDomain(): InventoryCommand = InventoryCommand(
    idempotencyKey = idempotencyKey,
    actorUserId = actorUserId,
    productId = productId,
    quantity = quantity,
    type = InventoryMovementType.fromDatabase(type),
    referenceId = referenceId,
    notes = notes,
    expectedVersion = expectedVersion,
    status = runCatching { InventoryCommandStatus.valueOf(status) }
        .getOrDefault(InventoryCommandStatus.PENDING),
    attemptCount = attemptCount,
    nextAttemptAtMillis = nextAttemptAtMillis,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    movementId = movementRemoteId,
    productVersion = productVersion,
    lastErrorCode = lastErrorCode,
)
