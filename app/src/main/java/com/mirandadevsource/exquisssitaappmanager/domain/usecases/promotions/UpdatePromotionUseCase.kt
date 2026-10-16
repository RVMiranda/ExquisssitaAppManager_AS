package com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import javax.inject.Inject

class UpdatePromotionUseCase @Inject constructor(private val repository: PromotionsRepository) {
    suspend operator fun invoke(
        id: String,
        titulo: String,
        descripcion: String,
        terminosCondiciones: String?,
        imagenUrl: String? = null,
        fechaVencimiento: String? = null,
        activo: Boolean,
    ): Result<Promotion> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("La promoción es obligatoria."))
        if (titulo.isBlank()) return Result.failure(IllegalArgumentException("El título es obligatorio."))
        if (descripcion.isBlank()) return Result.failure(IllegalArgumentException("La descripción es obligatoria."))
        return repository.updatePromotion(id, titulo, descripcion, terminosCondiciones, imagenUrl, fechaVencimiento, activo)
    }
}
