package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion

interface PromotionsRepository {
    suspend fun getPromotions(includeInactive: Boolean = false): Result<List<Promotion>>

    suspend fun createPromotion(
        titulo: String,
        descripcion: String,
        terminosCondiciones: String?,
        imagenUrl: String?,
        fechaVencimiento: String?,
    ): Result<Promotion>

    suspend fun updatePromotion(
        id: String,
        titulo: String,
        descripcion: String,
        terminosCondiciones: String?,
        imagenUrl: String?,
        fechaVencimiento: String?,
        activo: Boolean,
    ): Result<Promotion>

    suspend fun setPromotionActive(id: String, activo: Boolean): Result<Promotion>
}
