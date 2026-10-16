package com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import javax.inject.Inject

class GetPromotionsUseCase @Inject constructor(private val repository: PromotionsRepository) {
    suspend operator fun invoke(includeInactive: Boolean = false): Result<List<Promotion>> =
        repository.getPromotions(includeInactive)
}
