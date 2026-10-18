package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.CustomerCacheEntity

@Dao
interface CustomerCacheDao {
    @Query("SELECT * FROM customer_profile_cache WHERE telefono = :phone AND cacheActorUserId = :actorUserId ORDER BY fetchedAtMillis DESC LIMIT 1")
    suspend fun findByPhone(phone: String, actorUserId: String): CustomerCacheEntity?

    @Query("SELECT * FROM customer_profile_cache WHERE id = :customerId AND cacheActorUserId = :actorUserId LIMIT 1")
    suspend fun findById(customerId: String, actorUserId: String): CustomerCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CustomerCacheEntity)
}
