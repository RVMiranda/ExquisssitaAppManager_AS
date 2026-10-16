package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PromotionEntity

@Dao
interface PromotionDao {
    @Query("SELECT * FROM promotion_cache WHERE (:includeInactive = 1 OR activo = 1) ORDER BY fechaPublicacion DESC, id DESC")
    suspend fun getPromotions(includeInactive: Boolean): List<PromotionEntity>

    @Query("SELECT * FROM promotion_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PromotionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(promotion: PromotionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(promotions: List<PromotionEntity>)

    @Query("DELETE FROM promotion_cache")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(promotions: List<PromotionEntity>) {
        clearAll()
        upsertAll(promotions)
    }
}
