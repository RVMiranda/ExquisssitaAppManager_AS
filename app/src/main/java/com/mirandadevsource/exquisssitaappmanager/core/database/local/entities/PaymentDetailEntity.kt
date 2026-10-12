package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payment_detail_cache", indices = [Index("transaccionId"), Index("detalleOrdenId")])
data class PaymentDetailEntity(
    @PrimaryKey val id: String,
    val transaccionId: String,
    val detalleOrdenId: String,
    val cantidad: Int,
    val montoCentavos: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
