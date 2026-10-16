package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.InventoryCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryCommandDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: InventoryCommandEntity)

    @Update
    suspend fun update(command: InventoryCommandEntity)

    @Query("SELECT * FROM inventory_command_outbox WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getByKey(key: String): InventoryCommandEntity?

    @Query("SELECT * FROM inventory_command_outbox WHERE actorUserId = :actorId AND status IN ('PENDING', 'PROCESSING', 'CONFIRMED_AWAITING_REFRESH') AND (nextAttemptAtMillis IS NULL OR nextAttemptAtMillis <= :now) ORDER BY createdAtMillis, idempotencyKey")
    suspend fun getRecoverableCommandsForActor(actorId: String, now: Long): List<InventoryCommandEntity>

    @Query("SELECT * FROM inventory_command_outbox WHERE actorUserId = :actorId AND status != 'SYNCED' ORDER BY createdAtMillis DESC")
    fun observeUnresolvedCommands(actorId: String): Flow<List<InventoryCommandEntity>>

    @Query("SELECT COUNT(*) FROM inventory_command_outbox WHERE status IN ('PENDING', 'PROCESSING', 'CONFIRMED_AWAITING_REFRESH')")
    fun observePendingCount(): Flow<Int>
}
