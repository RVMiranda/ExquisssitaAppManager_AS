package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    suspend fun getProducts(includeInactive: Boolean = false): Result<List<Product>>
    suspend fun getProductPage(includeInactive: Boolean = false, page: PageRequest = PageRequest()): Result<DataPage<Product>> =
        getProducts(includeInactive).map { pageFromCompleteList(it, page, "products:${if (includeInactive) "all" else "active"}") }

    suspend fun createProduct(
        nombre: String,
        precioCentavos: Long,
        categoria: String,
        controlaInventario: Boolean,
        stockMinimo: Int,
    ): Result<Product>

    suspend fun updateProduct(
        id: String,
        nombre: String,
        precioCentavos: Long,
        categoria: String,
        controlaInventario: Boolean,
        stockMinimo: Int,
        activo: Boolean,
    ): Result<Product>

    suspend fun getMovements(
        productId: String,
        offset: Int = 0,
        limit: Int = DEFAULT_MOVEMENT_PAGE_SIZE,
    ): Result<List<StockMovement>>

    suspend fun registerMovement(request: RegisterInventoryMovement): Result<InventoryCommand>

    suspend fun syncPendingCommands(): Result<Unit>

    fun observeInventoryCommands(): Flow<List<InventoryCommand>>

    companion object {
        const val DEFAULT_MOVEMENT_PAGE_SIZE = 50
    }
}
