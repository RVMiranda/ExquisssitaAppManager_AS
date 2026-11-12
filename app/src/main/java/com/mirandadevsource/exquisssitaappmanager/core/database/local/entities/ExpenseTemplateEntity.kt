package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_template_cache",
    indices = [
        Index(value = ["remoteId", "cacheActorUserId"], unique = true),
        Index(value = ["cacheActorUserId", "activo"]),
    ],
)
data class ExpenseTemplateEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val cacheActorUserId: String,
    val nombre: String,
    val categoria: String,
    /** Precio unitario sugerido en centavos MXN (ej. 2400 para $24.00/kg). */
    val precioUnitarioCents: Long,
    val unidadMedida: String,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val syncState: String = SYNCED,
    val localUpdatedAtMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING_UPDATE = "PENDING_UPDATE"
    }
}
