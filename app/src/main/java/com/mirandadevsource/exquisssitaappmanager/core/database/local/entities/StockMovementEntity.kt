package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_movement_cache",
    indices = [
        Index(value = ["productId", "createdAt"]),
        Index(value = ["idempotencyKey"], unique = true),
    ],
)
data class StockMovementEntity(
    @PrimaryKey val remoteId: String,
    val productId: String,
    val quantity: Int,
    val type: String,
    val referenceId: String?,
    val notes: String?,
    val createdBy: String?,
    val createdAt: String,
    val idempotencyKey: String,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
