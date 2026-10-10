package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employee_cache",
    indices = [Index(value = ["remoteId"], unique = true)],
)
data class EmployeeEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val userId: String?,
    val nombre: String,
    val apellido: String,
    val telefono: String?,
    val salarioCents: Long,
    val diaPago: Int,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val syncState: String = SYNCED,
    val localUpdatedAtMillis: Long = System.currentTimeMillis(),
    val lastSyncedAtMillis: Long? = null,
    val lastSyncError: String? = null,
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING_UPDATE = "PENDING_UPDATE"
        const val SYNC_ERROR = "SYNC_ERROR"
    }
}
