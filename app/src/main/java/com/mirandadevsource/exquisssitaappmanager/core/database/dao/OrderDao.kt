package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM order_cache WHERE actorUserId = :actorId OR syncState = 'SYNCED' ORDER BY fechaApertura DESC")
    fun observeAll(actorId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM order_cache WHERE actorUserId = :actorId OR syncState = 'SYNCED' ORDER BY fechaApertura DESC")
    suspend fun getAllForActor(actorId: String): List<OrderEntity>

    @Query("SELECT * FROM order_cache WHERE (actorUserId = :actorId OR syncState = 'SYNCED') AND (:openOnly = 0 OR estado = 'Abierta') AND (:fromInclusive IS NULL OR fechaApertura >= :fromInclusive) AND (:toExclusive IS NULL OR fechaApertura < :toExclusive) ORDER BY fechaApertura DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(actorId: String, openOnly: Boolean, fromInclusive: String?, toExclusive: String?, limit: Int, offset: Int): List<OrderEntity>

    @Query("SELECT * FROM order_cache WHERE mesaId = :tableId AND (actorUserId = :actorId OR syncState = 'SYNCED') ORDER BY fechaApertura DESC")
    fun observeForTable(tableId: String, actorId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM order_cache WHERE id = :orderId AND (actorUserId = :actorId OR syncState = 'SYNCED') LIMIT 1")
    fun observeById(orderId: String, actorId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM order_cache WHERE id = :orderId LIMIT 1")
    suspend fun getById(orderId: String): OrderEntity?

    @Query("SELECT * FROM order_cache WHERE estado = 'Abierta' AND mesaId = :tableId")
    suspend fun getOpenForTable(tableId: String): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: OrderEntity)

    @Update
    suspend fun update(order: OrderEntity)

    @Query("UPDATE order_cache SET syncVersion = :version, syncState = 'SYNCED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: String, version: Long, updatedAt: String)

    @Query("UPDATE order_cache SET estado = :status, fechaCierre = :closedAt, syncState = 'PENDING' WHERE id = :id")
    suspend fun setLocalStatus(id: String, status: String, closedAt: String?)

    @Query("DELETE FROM order_cache WHERE id = :id")
    suspend fun deleteById(id: String)
}
