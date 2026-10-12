package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderDetailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDetailDao {
    @Query("SELECT d.* FROM order_detail_cache d JOIN order_cache o ON o.id = d.ordenId WHERE d.ordenId = :orderId AND o.actorUserId = :actorId ORDER BY d.createdAt, d.id")
    fun observeForOrder(orderId: String, actorId: String): Flow<List<OrderDetailEntity>>

    @Query("SELECT * FROM order_detail_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OrderDetailEntity?

    @Query("SELECT * FROM order_detail_cache WHERE ordenId = :orderId ORDER BY createdAt, id")
    suspend fun getForOrder(orderId: String): List<OrderDetailEntity>

    @Query("UPDATE order_detail_cache SET cantidad = :quantity, syncState = 'PENDING' WHERE id = :id")
    suspend fun setLocalQuantity(id: String, quantity: Int)

    @Query("UPDATE order_detail_cache SET estadoPago = 'Cancelado', syncState = 'PENDING' WHERE id = :id")
    suspend fun markLocallyCancelled(id: String)

    @Query("SELECT * FROM order_detail_cache")
    suspend fun getAll(): List<OrderDetailEntity>

    @Query("SELECT d.* FROM order_detail_cache d JOIN order_cache o ON o.id = d.ordenId WHERE o.actorUserId = :actorId")
    suspend fun getForActor(actorId: String): List<OrderDetailEntity>

    @Query("DELETE FROM order_detail_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM order_detail_cache WHERE ordenId = :orderId")
    suspend fun deleteForOrder(orderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(details: List<OrderDetailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detail: OrderDetailEntity)

    @Update
    suspend fun update(detail: OrderDetailEntity)

    @Query("UPDATE order_detail_cache SET syncVersion = :version, syncState = 'SYNCED', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: String, version: Long, updatedAt: String)
}
