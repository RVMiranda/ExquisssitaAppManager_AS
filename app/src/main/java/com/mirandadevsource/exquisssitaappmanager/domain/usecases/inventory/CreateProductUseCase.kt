package com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import javax.inject.Inject

class CreateProductUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    suspend operator fun invoke(
        nombre: String,
        precioCentavos: Long,
        categoria: String,
        controlaInventario: Boolean,
        stockMinimo: Int = 0,
    ): Result<Product> {
        if (nombre.isBlank()) return Result.failure(IllegalArgumentException("El nombre del producto no puede estar vacío."))
        if (precioCentavos < 0) return Result.failure(IllegalArgumentException("El precio debe ser mayor o igual a cero."))
        if (categoria.isBlank()) return Result.failure(IllegalArgumentException("La categoría no puede estar vacía."))
        if (controlaInventario && stockMinimo < 0) {
            return Result.failure(IllegalArgumentException("El stock mínimo no puede ser negativo."))
        }

        return repository.createProduct(
            nombre = nombre.trim(),
            precioCentavos = precioCentavos,
            categoria = categoria.trim(),
            controlaInventario = controlaInventario,
            stockMinimo = if (controlaInventario) stockMinimo else 0,
        )
    }
}
