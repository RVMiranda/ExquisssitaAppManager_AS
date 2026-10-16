package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderCommandDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: OrderCommandEntity)

    @Update
    suspend fun update(command: OrderCommandEntity)

    @Query("SELECT * FROM order_command_outbox WHERE idempotencyKey = :key LIMIT 1")
    suspend fun getByKey(key: String): OrderCommandEntity?

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND entity = :entity AND aggregateId = :aggregateId AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH') ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getPendingForEntity(actorId: String, entity: String, aggregateId: String): OrderCommandEntity?

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND entity = :entity AND aggregateId = :aggregateId ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getLatestForEntity(actorId: String, entity: String, aggregateId: String): OrderCommandEntity?

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH') AND (nextAttemptAtMillis IS NULL OR nextAttemptAtMillis <= :now) ORDER BY createdAtMillis, idempotencyKey")
    suspend fun getRecoverableForActor(actorId: String, now: Long): List<OrderCommandEntity>

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND aggregateId = :aggregateId AND status != 'SYNCED' ORDER BY createdAtMillis, idempotencyKey")
    fun observeForOrder(actorId: String, aggregateId: String): Flow<List<OrderCommandEntity>>

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND aggregateId = :aggregateId AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH','REJECTED') ORDER BY createdAtMillis, idempotencyKey")
    suspend fun getUnresolvedForOrder(actorId: String, aggregateId: String): List<OrderCommandEntity>

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND aggregateId = :aggregateId AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH') ORDER BY createdAtMillis, idempotencyKey")
    suspend fun getRecoverableForOrder(actorId: String, aggregateId: String): List<OrderCommandEntity>

    @Query("SELECT COUNT(*) FROM order_command_outbox WHERE actorUserId = :actorId AND entityId = :entityId AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH')")
    suspend fun countPendingForEntity(actorId: String, entityId: String): Int

    @Query("SELECT COUNT(*) FROM order_command_outbox WHERE actorUserId = :actorId AND aggregateId = :orderId AND entity = 'registrar_pago' AND status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH')")
    suspend fun countPendingPayments(actorId: String, orderId: String): Int

    @Query("SELECT * FROM order_command_outbox WHERE actorUserId = :actorId AND entity = 'registrar_pago' AND aggregateId = :orderId AND status != 'SYNCED' ORDER BY createdAtMillis")
    fun observePaymentCommands(actorId: String, orderId: String): Flow<List<OrderCommandEntity>>

    @Query("SELECT COUNT(*) FROM order_command_outbox WHERE status IN ('PENDING','PROCESSING','CONFIRMED_AWAITING_REFRESH')")
    fun observePendingCount(): Flow<Int>
}
