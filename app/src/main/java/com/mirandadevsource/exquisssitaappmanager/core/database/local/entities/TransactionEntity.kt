package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_cache", indices = [Index("ordenId"), Index(value = ["idempotencyKey"], unique = true)])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val ordenId: String,
    val montoCentavos: Long,
    val metodoPago: String,
    val fecha: String,
    val idempotencyKey: String,
    val createdAt: String,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
