package com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import javax.inject.Inject

class CreatePromotionUseCase @Inject constructor(private val repository: PromotionsRepository) {
    suspend operator fun invoke(
        titulo: String,
        descripcion: String,
        terminosCondiciones: String?,
        imagenUrl: String? = null,
        fechaVencimiento: String? = null,
    ): Result<Promotion> {
        if (titulo.isBlank()) return Result.failure(IllegalArgumentException("El título es obligatorio."))
        if (descripcion.isBlank()) return Result.failure(IllegalArgumentException("La descripción es obligatoria."))
        return repository.createPromotion(titulo, descripcion, terminosCondiciones, imagenUrl, fechaVencimiento)
    }
}
