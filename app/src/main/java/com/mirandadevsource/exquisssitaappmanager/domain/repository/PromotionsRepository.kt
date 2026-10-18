package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList

interface PromotionsRepository {
    suspend fun getPromotions(includeInactive: Boolean = false): Result<List<Promotion>>
    suspend fun getPromotionPage(includeInactive: Boolean = false, page: PageRequest = PageRequest()): Result<DataPage<Promotion>> =
        getPromotions(includeInactive).map { pageFromCompleteList(it, page, "promotions:${if (includeInactive) "all" else "active"}") }

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
