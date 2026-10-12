package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_command_outbox",
    indices = [Index("actorUserId", "status", "createdAtMillis"), Index("aggregateId", "createdAtMillis")],
)
data class OrderCommandEntity(
    @PrimaryKey val idempotencyKey: String,
    val actorUserId: String,
    val aggregateId: String,
    val entity: String,
    val entityId: String,
    val operation: String,
    /** Immutable serialized JSON sent on every retry. */
    val payloadJson: String,
    /** 0 for INSERT; updates resolve the current Room version after prior commands. */
    val expectedVersion: Long?,
    val status: String = PENDING,
    val attemptCount: Int = 0,
    val nextAttemptAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val resultJson: String? = null,
    val lastErrorCode: String? = null,
) {
    companion object {
        const val PENDING = "PENDING"
        const val PROCESSING = "PROCESSING"
        const val CONFIRMED_AWAITING_REFRESH = "CONFIRMED_AWAITING_REFRESH"
        const val SYNCED = "SYNCED"
        const val REJECTED = "REJECTED"
    }
}
