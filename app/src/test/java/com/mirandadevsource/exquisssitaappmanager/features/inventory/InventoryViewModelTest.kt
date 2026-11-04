package com.mirandadevsource.exquisssitaappmanager.features.inventory

import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.CreateProductUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.GetProductsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.inventory.UpdateProductUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `parsePesosToCentavos parses user input with exact centavos without floating drift`() {
        assertEquals(3500L, InventoryUiState.parsePesosToCentavos("35"))
        assertEquals(3500L, InventoryUiState.parsePesosToCentavos("35.00"))
        assertEquals(3550L, InventoryUiState.parsePesosToCentavos("35.5"))
        assertEquals(120075L, InventoryUiState.parsePesosToCentavos("1,200.75"))
        assertEquals(120075L, InventoryUiState.parsePesosToCentavos("$1200.75"))
        assertNull(InventoryUiState.parsePesosToCentavos(""))
        assertNull(InventoryUiState.parsePesosToCentavos("-10"))
        assertNull(InventoryUiState.parsePesosToCentavos("abc"))
    }

    @Test
    fun `UiState computes low stock and categories correctly`() {
        val state = InventoryUiState(rawProducts = sampleProducts)

        assertEquals(1, state.lowStockCount) // p2 has current 2 <= min 5
        assertEquals(2, state.totalControlledCount)
        assertEquals(listOf("Aguas N", "Bebidas", "Postres", "Tacos"), state.availableCategories)
    }

    @Test
    fun `formCategoryOptions contains predefined categories and appends distinct existing categories`() {
        val state = InventoryUiState(rawProducts = sampleProducts)

        val expected = listOf(
            "Refrescos",
            "Jugos",
            "Aguas N",
            "Tacos",
            "Gorditas",
            "Carne",
            "Verdura",
            "Otros",
            "Bebidas",
            "Postres",
        )
        assertEquals(expected, state.formCategoryOptions)
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
        val fakeRepo = FakeInventoryRepository(sampleProducts.toMutableList())
        val useCase = GetProductsUseCase(fakeRepo)

        val result = useCase(includeInactive = true)
        val products = result.getOrThrow()

        assertEquals(4, products.size)
    }

    @Test
    fun `CreateProductUseCase creates product successfully`() = runBlocking {
        val list = sampleProducts.toMutableList()
        val fakeRepo = FakeInventoryRepository(list)
        val useCase = CreateProductUseCase(fakeRepo)

        val created = useCase(
            nombre = "Gringas de Pastor",
            precioCentavos = 4500,
            categoria = "Tacos",
            controlaInventario = true,
            stockMinimo = 10,
        ).getOrThrow()

        assertNotNull(created.id)
        assertEquals("Gringas de Pastor", created.nombre)
        assertEquals(4500L, created.precioCentavos)
        assertEquals(5, list.size)
    }

    @Test
    fun `UpdateProductUseCase modifies product properly`() = runBlocking {
        val list = sampleProducts.toMutableList()
        val fakeRepo = FakeInventoryRepository(list)
        val useCase = UpdateProductUseCase(fakeRepo)

        val updated = useCase(
            id = "p1",
            nombre = "Agua Melon Especial",
            precioCentavos = 4000,
            categoria = "Aguas N",
            controlaInventario = false,
            stockMinimo = 0,
            activo = true,
        ).getOrThrow()

        assertEquals("Agua Melon Especial", updated.nombre)
        assertEquals(4000L, updated.precioCentavos)
    }

    private class FakeInventoryRepository(
        private val products: MutableList<Product>,
    ) : InventoryRepository {
        override suspend fun getProducts(includeInactive: Boolean): Result<List<Product>> {
            return Result.success(if (includeInactive) products else products.filter { it.activo })
        }

        override suspend fun createProduct(
            nombre: String,
            precioCentavos: Long,
            categoria: String,
            controlaInventario: Boolean,
            stockMinimo: Int,
            precioAbierto: Boolean,
        ): Result<Product> {
            val p = Product(
                id = "created-${products.size + 1}",
                nombre = nombre,
                precioCentavos = precioCentavos,
                categoria = categoria,
                controlaInventario = controlaInventario,
                precioAbierto = precioAbierto,
                stockActual = 0,
                stockMinimo = stockMinimo,
                activo = true,
                createdAt = "2026-10-11T00:00:00Z",
                updatedAt = "2026-10-11T00:00:00Z",
                syncVersion = 1,
            )
            products.add(p)
            return Result.success(p)
        }

        override suspend fun updateProduct(
            id: String,
            nombre: String,
            precioCentavos: Long,
            categoria: String,
            controlaInventario: Boolean,
            stockMinimo: Int,
            activo: Boolean,
            precioAbierto: Boolean,
        ): Result<Product> {
            val idx = products.indexOfFirst { it.id == id }
            if (idx == -1) return Result.failure(NoSuchElementException())
            val updated = products[idx].copy(
                nombre = nombre,
                precioCentavos = precioCentavos,
                categoria = categoria,
                controlaInventario = controlaInventario,
                precioAbierto = precioAbierto,
                stockMinimo = stockMinimo,
                activo = activo,
            )
            products[idx] = updated
            return Result.success(updated)
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
