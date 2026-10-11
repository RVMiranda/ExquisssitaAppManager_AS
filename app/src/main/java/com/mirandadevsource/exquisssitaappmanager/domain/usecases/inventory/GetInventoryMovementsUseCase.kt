package com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import javax.inject.Inject

class GetInventoryMovementsUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    suspend operator fun invoke(
        productId: String,
        offset: Int = 0,
        limit: Int = InventoryRepository.DEFAULT_MOVEMENT_PAGE_SIZE,
    ): Result<List<StockMovement>> = repository.getMovements(productId, offset, limit)
}
