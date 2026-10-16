package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "promotion_cache")
data class PromotionEntity(
    @PrimaryKey val id: String,
    val titulo: String,
    val descripcion: String,
    val terminosCondiciones: String?,
    val imagenUrl: String?,
    val fechaPublicacion: String,
    val fechaVencimiento: String?,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
