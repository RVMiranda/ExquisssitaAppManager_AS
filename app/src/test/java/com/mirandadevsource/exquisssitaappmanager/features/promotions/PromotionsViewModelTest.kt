package com.mirandadevsource.exquisssitaappmanager.features.promotions

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.CreatePromotionUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.GetPromotionsUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.SetPromotionActiveUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.promotions.UpdatePromotionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PromotionsViewModelTest {

    private val samplePromotions = listOf(
        Promotion(
            id = "promo-1",
            titulo = "2x1 en Refrescos",
            descripcion = "Válido en refrescos de 600ml los días jueves",
            terminosCondiciones = "Solo consumo en comedor. No acumulable.",
            imagenUrl = null,
            fechaPublicacion = "2026-10-01T10:00:00Z",
            fechaVencimiento = LocalDate.now().plusDays(30).toString(),
            activo = true,
            createdAt = "2026-10-01T10:00:00Z",
            updatedAt = "2026-10-01T10:00:00Z",
            syncVersion = 1,
        ),
        Promotion(
            id = "promo-2",
            titulo = "Combo Familiar Gorditas",
            descripcion = "10 gorditas surtidas + 2 jarritas",
            terminosCondiciones = "Válido fines de semana",
            imagenUrl = null,
            fechaPublicacion = "2026-09-15T10:00:00Z",
            fechaVencimiento = null, // Permanente
            activo = true,
            createdAt = "2026-09-15T10:00:00Z",
            updatedAt = "2026-09-15T10:00:00Z",
            syncVersion = 1,
        ),
        Promotion(
            id = "promo-3",
            titulo = "Descuento en Tacos",
            descripcion = "15% de descuento en tacos de pastor",
            terminosCondiciones = null,
            imagenUrl = null,
            fechaPublicacion = "2026-08-01T10:00:00Z",
            fechaVencimiento = LocalDate.now().plusDays(10).toString(),
            activo = false, // Inactiva
            createdAt = "2026-08-01T10:00:00Z",
            updatedAt = "2026-08-01T10:00:00Z",
            syncVersion = 1,
        ),
        Promotion(
            id = "promo-4",
            titulo = "Promoción de Verano Pasada",
            descripcion = "Nieve gratis en la compra de 5 tacos",
            terminosCondiciones = "Hasta agotar existencias",
            imagenUrl = null,
            fechaPublicacion = "2026-06-01T10:00:00Z",
            fechaVencimiento = "2026-07-01", // Vencida
            activo = true,
            createdAt = "2026-06-01T10:00:00Z",
            updatedAt = "2026-06-01T10:00:00Z",
            syncVersion = 1,
        ),
    )

    private class FakePromotionsRepository(
        initialList: List<Promotion> = emptyList(),
        var isOnline: Boolean = true,
    ) : PromotionsRepository {
        val promotions = initialList.toMutableList()

        override suspend fun getPromotions(includeInactive: Boolean): Result<List<Promotion>> {
            return Result.success(if (includeInactive) promotions else promotions.filter { it.activo })
        }

        override suspend fun createPromotion(
            titulo: String,
            descripcion: String,
            terminosCondiciones: String?,
            imagenUrl: String?,
            fechaVencimiento: String?,
        ): Result<Promotion> {
            if (!isOnline) {
                return Result.failure(IllegalStateException("Se requiere conexión para crear o editar promociones; no se guardó ningún cambio."))
            }
            val newPromo = Promotion(
                id = "promo-${promotions.size + 1}",
                titulo = titulo,
                descripcion = descripcion,
                terminosCondiciones = terminosCondiciones,
                imagenUrl = imagenUrl,
                fechaPublicacion = "2026-10-12T12:00:00Z",
                fechaVencimiento = fechaVencimiento,
                activo = true,
                createdAt = "2026-10-12T12:00:00Z",
                updatedAt = "2026-10-12T12:00:00Z",
                syncVersion = 1,
            )
            promotions.add(0, newPromo)
            return Result.success(newPromo)
        }

        override suspend fun updatePromotion(
            id: String,
            titulo: String,
            descripcion: String,
            terminosCondiciones: String?,
            imagenUrl: String?,
            fechaVencimiento: String?,
            activo: Boolean,
        ): Result<Promotion> {
            if (!isOnline) {
                return Result.failure(IllegalStateException("Se requiere conexión para crear o editar promociones; no se guardó ningún cambio."))
            }
            val index = promotions.indexOfFirst { it.id == id }
            if (index == -1) return Result.failure(NoSuchElementException("No encontrada"))
            val updated = promotions[index].copy(
                titulo = titulo,
                descripcion = descripcion,
                terminosCondiciones = terminosCondiciones,
                imagenUrl = imagenUrl,
                fechaVencimiento = fechaVencimiento,
                activo = activo,
            )
            promotions[index] = updated
            return Result.success(updated)
        }

        override suspend fun setPromotionActive(id: String, activo: Boolean): Result<Promotion> {
            if (!isOnline) {
                return Result.failure(IllegalStateException("Se requiere conexión para crear o editar promociones; no se guardó ningún cambio."))
            }
            val index = promotions.indexOfFirst { it.id == id }
            if (index == -1) return Result.failure(NoSuchElementException("No encontrada"))
            val updated = promotions[index].copy(activo = activo)
            promotions[index] = updated
            return Result.success(updated)
        }
    }

    @Test
    fun `PromotionsUiState calculates total, active, inactive, and expired KPIs accurately`() {
        val state = PromotionsUiState(
            promotions = samplePromotions,
            todayDate = "2026-10-12",
        )

        assertEquals(4, state.totalCount)
        // promo-1 (activo, no vencida) y promo-2 (activo, permanente) -> 2 activas
        assertEquals(2, state.activeCount)
        // promo-3 (inactiva) -> 1 inactiva
        assertEquals(1, state.inactiveCount)
        // promo-4 (vencida en 2026-07-01 < 2026-10-12) -> 1 vencida
        assertEquals(1, state.expiredCount)
    }

    @Test
    fun `PromotionsUiState filters by TODAS, ACTIVAS, INACTIVAS, and VENCIDAS correctly`() {
        val baseState = PromotionsUiState(
            promotions = samplePromotions,
            todayDate = "2026-10-12",
        )

        // Todas
        val todas = baseState.copy(selectedFilter = PromotionFilter.TODAS).filteredPromotions
        assertEquals(4, todas.size)

        // Activas
        val activas = baseState.copy(selectedFilter = PromotionFilter.ACTIVAS).filteredPromotions
        assertEquals(2, activas.size)
        assertTrue(activas.all { it.activo && !baseState.isExpired(it) })

        // Inactivas
        val inactivas = baseState.copy(selectedFilter = PromotionFilter.INACTIVAS).filteredPromotions
        assertEquals(1, inactivas.size)
        assertEquals("promo-3", inactivas.first().id)

        // Vencidas
        val vencidas = baseState.copy(selectedFilter = PromotionFilter.VENCIDAS).filteredPromotions
        assertEquals(1, vencidas.size)
        assertEquals("promo-4", vencidas.first().id)
    }

    @Test
    fun `PromotionsUiState searchQuery matches by title, description, and terms`() {
        val baseState = PromotionsUiState(
            promotions = samplePromotions,
            todayDate = "2026-10-12",
        )

        // Búsqueda por título
        val porTitulo = baseState.copy(searchQuery = "Refrescos").filteredPromotions
        assertEquals(1, porTitulo.size)
        assertEquals("promo-1", porTitulo.first().id)

        // Búsqueda por descripción
        val porDesc = baseState.copy(searchQuery = "gorditas surtidas").filteredPromotions
        assertEquals(1, porDesc.size)
        assertEquals("promo-2", porDesc.first().id)

        // Búsqueda por términos y condiciones (nuevo campo)
        val porTerminos = baseState.copy(searchQuery = "comedor").filteredPromotions
        assertEquals(1, porTerminos.size)
        assertEquals("promo-1", porTerminos.first().id)

        // Sin coincidencias
        val sinMatch = baseState.copy(searchQuery = "PizzaHut").filteredPromotions
        assertEquals(0, sinMatch.size)
    }

    @Test
    fun `CreatePromotionUseCase validates empty title or empty description`() = runBlocking {
        val repo = FakePromotionsRepository()
        val useCase = CreatePromotionUseCase(repo)

        val emptyTitleResult = useCase(
            titulo = "   ",
            descripcion = "Alguna descripción válida",
            terminosCondiciones = null,
        )
        assertTrue(emptyTitleResult.isFailure)
        assertEquals("El título es obligatorio.", emptyTitleResult.exceptionOrNull()?.message)

        val emptyDescResult = useCase(
            titulo = "Título Válido",
            descripcion = "  ",
            terminosCondiciones = null,
        )
        assertTrue(emptyDescResult.isFailure)
        assertEquals("La descripción es obligatoria.", emptyDescResult.exceptionOrNull()?.message)
    }

    @Test
    fun `CreatePromotionUseCase delegates successfully with optional terms and expiration date`() = runBlocking {
        val repo = FakePromotionsRepository()
        val useCase = CreatePromotionUseCase(repo)

        val result = useCase(
            titulo = "Martes de Gorditas",
            descripcion = "Gorditas a $15 MXN",
            terminosCondiciones = "Solo consumo en sucursal",
            imagenUrl = null,
            fechaVencimiento = "2026-11-30",
        )

        assertTrue(result.isSuccess)
        val created = result.getOrThrow()
        assertEquals("Martes de Gorditas", created.titulo)
        assertEquals("Solo consumo en sucursal", created.terminosCondiciones)
        assertEquals("2026-11-30", created.fechaVencimiento)
        assertTrue(created.activo)
        assertEquals(1, repo.promotions.size)
    }

    @Test
    fun `UpdatePromotionUseCase updates title, terms, and active state`() = runBlocking {
        val repo = FakePromotionsRepository(samplePromotions)
        val useCase = UpdatePromotionUseCase(repo)

        val result = useCase(
            id = "promo-1",
            titulo = "2x1 en Refrescos Actualizado",
            descripcion = "Válido cualquier día de la semana",
            terminosCondiciones = "Nuevo término: hasta agotar existencias",
            imagenUrl = "https://exquisssita.com/promo.png",
            fechaVencimiento = "2026-12-31",
            activo = true,
        )

        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals("2x1 en Refrescos Actualizado", updated.titulo)
        assertEquals("Nuevo término: hasta agotar existencias", updated.terminosCondiciones)
        assertEquals("https://exquisssita.com/promo.png", updated.imagenUrl)
    }

    @Test
    fun `SetPromotionActiveUseCase toggles promotion active flag without physical deletion`() = runBlocking {
        val repo = FakePromotionsRepository(samplePromotions)
        val useCase = SetPromotionActiveUseCase(repo)

        // Pausar / Desactivar promo-1 (baja operativa)
        val deactivationResult = useCase("promo-1", false)
        assertTrue(deactivationResult.isSuccess)
        val deactivated = deactivationResult.getOrThrow()
        assertFalse(deactivated.activo)

        // Reactivar promo-3
        val reactivateResult = useCase("promo-3", true)
        assertTrue(reactivateResult.isSuccess)
        val reactivated = reactivateResult.getOrThrow()
        assertTrue(reactivated.activo)
    }

    @Test
    fun `FakePromotionsRepository blocks write operations when offline`() = runBlocking {
        val repo = FakePromotionsRepository(samplePromotions, isOnline = false)
        val createUseCase = CreatePromotionUseCase(repo)
        val setActiveUseCase = SetPromotionActiveUseCase(repo)

        val createResult = createUseCase(
            titulo = "Promo sin red",
            descripcion = "No debería pasar",
            terminosCondiciones = null,
        )
        assertTrue(createResult.isFailure)
        assertTrue(createResult.exceptionOrNull()?.message?.contains("Se requiere conexión") == true)

        val toggleResult = setActiveUseCase("promo-1", false)
        assertTrue(toggleResult.isFailure)
        assertTrue(toggleResult.exceptionOrNull()?.message?.contains("Se requiere conexión") == true)
    }

    @Test
    fun `GetPromotionsUseCase returns all promotions including inactive when requested`() = runBlocking {
        val repo = FakePromotionsRepository(samplePromotions)
        val useCase = GetPromotionsUseCase(repo)

        val allPromos = useCase(includeInactive = true).getOrThrow()
        assertEquals(4, allPromos.size)

        val onlyActive = useCase(includeInactive = false).getOrThrow()
        assertEquals(3, onlyActive.size)
        assertFalse(onlyActive.any { it.id == "promo-3" })
    }
}
