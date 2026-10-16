package com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import javax.inject.Inject

class SetPromotionActiveUseCase @Inject constructor(private val repository: PromotionsRepository) {
    suspend operator fun invoke(id: String, activo: Boolean): Result<Promotion> =
        repository.setPromotionActive(id, activo)
}
