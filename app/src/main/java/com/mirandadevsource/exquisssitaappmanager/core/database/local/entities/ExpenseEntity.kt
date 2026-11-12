package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_cache",
    indices = [
        Index(value = ["remoteId", "cacheActorUserId"], unique = true),
        Index(value = ["cacheActorUserId", "fecha"]),
        Index(value = ["cacheActorUserId", "categoria"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val cacheActorUserId: String,
    /** Fecha de negocio YYYY-MM-DD. */
    val fecha: String,
    val concepto: String,
    val categoria: String,
    /** Monto total del gasto en centavos enteros MXN (ej. 24000 para $240.00). */
    val montoCents: Long,
    /** Cantidad numérica opcional (ej. 10.0 kg). */
    val cantidad: Double? = null,
    /** Precio unitario en centavos si aplica. */
    val precioUnitarioCents: Long? = null,
    val metodoPago: String = "Efectivo",
    val plantillaId: String? = null,
    val notas: String? = null,
    val registradoPor: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long = 1L,
    val syncState: String = SYNCED,
    val syncError: String? = null,
    val localUpdatedAtMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SYNCED = "SYNCED"
        const val PENDING_INSERT = "PENDING_INSERT"
        const val PENDING_UPDATE = "PENDING_UPDATE"
        const val PENDING_DELETE = "PENDING_DELETE"
        const val SYNC_ERROR = "SYNC_ERROR"
    }
}
