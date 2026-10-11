package com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import javax.inject.Inject

class GetProductsUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    suspend operator fun invoke(includeInactive: Boolean = false): Result<List<Product>> =
        repository.getProducts(includeInactive)
}
