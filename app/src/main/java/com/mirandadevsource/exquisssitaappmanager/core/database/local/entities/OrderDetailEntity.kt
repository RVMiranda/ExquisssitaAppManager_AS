package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "order_detail_cache", indices = [Index("ordenId"), Index("productoId")])
data class OrderDetailEntity(
    @PrimaryKey val id: String,
    val ordenId: String,
    val productoId: String,
    val cantidad: Int,
    val precioUnitarioCentavos: Long,
    val estadoPago: String,
    val notas: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val syncState: String = SYNCED,
    val cachedAtMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING = "PENDING"
    }
}
