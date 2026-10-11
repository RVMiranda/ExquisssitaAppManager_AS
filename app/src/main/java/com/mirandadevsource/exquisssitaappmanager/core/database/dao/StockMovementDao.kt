package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.StockMovementEntity

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM inventory_movement_cache WHERE productId = :productId ORDER BY createdAt DESC, remoteId DESC LIMIT :limit OFFSET :offset")
    suspend fun getForProduct(productId: String, limit: Int, offset: Int): List<StockMovementEntity>

    @Query("SELECT * FROM inventory_movement_cache WHERE remoteId = :id LIMIT 1")
    suspend fun getByRemoteId(id: String): StockMovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movements: List<StockMovementEntity>)
}
