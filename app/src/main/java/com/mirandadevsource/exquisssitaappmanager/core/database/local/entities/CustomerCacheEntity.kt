package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index

/** Minimal profile cache. QR tokens and their digests are intentionally never stored here. */
@Entity(
    tableName = "customer_profile_cache",
    primaryKeys = ["id", "cacheActorUserId"],
    indices = [
        Index(value = ["cacheActorUserId", "telefono"]),
        Index(value = ["cacheActorUserId", "fetchedAtMillis"]),
    ],
)
data class CustomerCacheEntity(
    val id: String,
    val cacheActorUserId: String,
    val nombre: String,
    val telefono: String?,
    val visitasTotales: Int,
    val puntosLealtad: Int,
    val fechaRegistro: String?,
    val fetchedAtMillis: Long,
)
