package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_command_outbox",
    indices = [Index(value = ["actorUserId", "status", "createdAtMillis"])],
)
data class InventoryCommandEntity(
    @PrimaryKey val idempotencyKey: String,
    val actorUserId: String,
    val productId: String,
    val quantity: Int,
    val type: String,
    val referenceId: String?,
    val notes: String?,
    val expectedVersion: Long?,
    val status: String,
    val attemptCount: Int = 0,
    val nextAttemptAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val movementRemoteId: String? = null,
    val productVersion: Long? = null,
    val lastErrorCode: String? = null,
)
