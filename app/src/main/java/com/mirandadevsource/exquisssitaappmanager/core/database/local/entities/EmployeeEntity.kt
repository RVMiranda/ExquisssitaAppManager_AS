package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employee_cache",
    indices = [Index(value = ["remoteId", "cacheActorUserId"], unique = true)],
)
data class EmployeeEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    /** Actor-scoped cache owner. Null rows are legacy and are never served to an authenticated actor. */
    val cacheActorUserId: String? = null,
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
    /** Actor who created the outstanding local edit; null for synced snapshots and legacy edits. */
    val updateActorUserId: String? = null,
    val localUpdatedAtMillis: Long = System.currentTimeMillis(),
    val lastSyncedAtMillis: Long? = null,
    val lastSyncError: String? = null,
    /** Latest server row captured when an optimistic version check fails. */
    val conflictServerJson: String? = null,
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING_UPDATE = "PENDING_UPDATE"
        const val SYNC_ERROR = "SYNC_ERROR"
        const val CONFLICT = "CONFLICT"
    }
}
