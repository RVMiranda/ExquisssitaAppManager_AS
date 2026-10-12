package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payment_stock_cache", indices = [Index("transaccionId"), Index("detalleOrdenId"), Index(value = ["idempotencyKey"], unique = true)])
data class PaymentStockEntity(
    @PrimaryKey val id: String,
    val transaccionId: String,
    val detalleOrdenId: String,
    val productoId: String,
    val cantidad: Int,
    val movimientoId: String,
    val idempotencyKey: String,
    val createdAt: String,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
