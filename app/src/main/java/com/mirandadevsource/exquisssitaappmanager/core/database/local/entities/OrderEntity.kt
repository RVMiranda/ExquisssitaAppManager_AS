package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_cache",
    indices = [Index("mesaId", "estado"), Index("estado"), Index("actorUserId")],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val mesaId: String?,
    val clienteId: String?,
    val tipoServicio: String,
    val estado: String,
    val fechaApertura: String,
    val fechaCierre: String?,
    val notas: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val actorUserId: String,
    val syncState: String = SYNCED,
    val cachedAtMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING = "PENDING"
    }
}
