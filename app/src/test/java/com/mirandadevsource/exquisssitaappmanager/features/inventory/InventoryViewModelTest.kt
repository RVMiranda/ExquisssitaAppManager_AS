package com.mirandadevsource.exquisssitaappmanager.features.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.GetProductsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryViewModelTest {

    private val sampleProducts = listOf(
        Product(
            id = "p1",
            nombre = "Agua Melon",
            precioCentavos = 3500,
            categoria = "Aguas N",
            controlaInventario = false,
            stockActual = 0,
            stockMinimo = 0,
            activo = true,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Product(
            id = "p2",
            nombre = "Tacos al Pastor",
            precioCentavos = 2000,
            categoria = "Tacos",
            controlaInventario = true,
            stockActual = 2,
            stockMinimo = 5,
            activo = true,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Product(
            id = "p3",
            nombre = "Refresco Cola",
            precioCentavos = 2500,
            categoria = "Bebidas",
            controlaInventario = true,
            stockActual = 15,
            stockMinimo = 5,
            activo = true,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
        Product(
            id = "p4",
            nombre = "Postre Antiguo",
            precioCentavos = 4000,
            categoria = "Postres",
            controlaInventario = false,
            stockActual = 0,
            stockMinimo = 0,
            activo = false,
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = "2026-10-01T00:00:00Z",
            syncVersion = 1,
        ),
    )

    @Test
    fun `formatPrice formats centavos to Mexican pesos correctly`() {
        assertEquals("$35.00 MXN", InventoryUiState.formatPrice(3500))
        assertEquals("$1,200.00 MXN", InventoryUiState.formatPrice(120000))
        assertEquals("$0.50 MXN", InventoryUiState.formatPrice(50))
    }

    @Test
    fun `UiState computes low stock and categories correctly`() {
        val state = InventoryUiState(rawProducts = sampleProducts)

        assertEquals(1, state.lowStockCount) // p2 has current 2 <= min 5
        assertEquals(2, state.totalControlledCount)
        assertEquals(listOf("Aguas N", "Bebidas", "Postres", "Tacos"), state.availableCategories)
    }

    @Test
    fun `filtering by category returns matching products`() {
        val state = InventoryUiState(
            rawProducts = sampleProducts,
            selectedCategory = "Tacos",
        )

        val results = state.filteredProducts
        assertEquals(1, results.size)
        assertEquals("Tacos al Pastor", results.first().nombre)
    }

    @Test
    fun `filtering by search query matches product name and category`() {
        val state = InventoryUiState(
            rawProducts = sampleProducts,
            searchQuery = "melon",
        )

        val results = state.filteredProducts
        assertEquals(1, results.size)
        assertEquals("Agua Melon", results.first().nombre)
    }

    @Test
    fun `filtering by low stock returns only alert items`() {
        val state = InventoryUiState(
            rawProducts = sampleProducts,
            stockFilter = StockFilter.BAJO_STOCK,
        )

        val results = state.filteredProducts
        assertEquals(1, results.size)
        assertEquals("Tacos al Pastor", results.first().nombre)
    }

    @Test
    fun `filtering by status isolates active and inactive items`() {
        val activeState = InventoryUiState(
            rawProducts = sampleProducts,
            statusFilter = ProductStatusFilter.ACTIVOS,
        )
        assertEquals(3, activeState.filteredProducts.size)

        val inactiveState = InventoryUiState(
            rawProducts = sampleProducts,
            statusFilter = ProductStatusFilter.INACTIVOS,
        )
        assertEquals(1, inactiveState.filteredProducts.size)
        assertEquals("Postre Antiguo", inactiveState.filteredProducts.first().nombre)
    }

    @Test
    fun `GetProductsUseCase delegates to repository properly`() = runBlocking {
        val fakeRepo = FakeInventoryRepository(sampleProducts)
        val useCase = GetProductsUseCase(fakeRepo)

        val result = useCase(includeInactive = true)
        val products = result.getOrThrow()

        assertEquals(4, products.size)
    }

    private class FakeInventoryRepository(
        private val products: List<Product>,
    ) : InventoryRepository {
        override suspend fun getProducts(includeInactive: Boolean): Result<List<Product>> {
            return Result.success(if (includeInactive) products else products.filter { it.activo })
        }

        override suspend fun getMovements(
            productId: String,
            offset: Int,
            limit: Int,
        ): Result<List<StockMovement>> = Result.success(emptyList())

        override suspend fun registerMovement(
            request: RegisterInventoryMovement,
        ): Result<InventoryCommand> = Result.failure(UnsupportedOperationException())

        override suspend fun syncPendingCommands(): Result<Unit> = Result.success(Unit)

        override fun observeInventoryCommands(): Flow<List<InventoryCommand>> =
            flowOf(emptyList())
    }
}
