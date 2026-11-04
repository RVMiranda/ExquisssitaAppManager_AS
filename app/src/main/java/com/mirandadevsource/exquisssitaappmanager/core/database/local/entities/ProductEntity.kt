package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_cache")
data class ProductEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val precioCentavos: Long,
    val categoria: String,
    val controlaInventario: Boolean,
    val precioAbierto: Boolean = false,
    val stockActual: Int?,
    val stockMinimo: Int?,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
