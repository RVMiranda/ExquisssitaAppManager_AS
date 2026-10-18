package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ProductEntity

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_cache WHERE (:includeInactive = 1 OR activo = 1) ORDER BY categoria COLLATE NOCASE, nombre COLLATE NOCASE")
    suspend fun getProducts(includeInactive: Boolean): List<ProductEntity>

    @Query("SELECT * FROM product_cache WHERE (:includeInactive = 1 OR activo = 1) ORDER BY categoria COLLATE NOCASE, nombre COLLATE NOCASE LIMIT :limit OFFSET :offset")
    suspend fun getProductPage(includeInactive: Boolean, limit: Int, offset: Int): List<ProductEntity>

    @Query("SELECT * FROM product_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)
}
