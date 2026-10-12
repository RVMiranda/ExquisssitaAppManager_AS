package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "table_cache", indices = [Index("numeroMesa"), Index("estado")])
data class TableEntity(
    @PrimaryKey val id: String,
    val numeroMesa: String,
    val estado: String,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val syncState: String = SYNCED,
    val cachedAtMillis: Long = System.currentTimeMillis(),
    val actorUserId: String? = null,
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING = "PENDING"
    }
}
