package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PromotionDao
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.CreatePromotionRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.PromotionDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.UpdatePromotionActiveDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.UpdatePromotionRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromFetchedWindow
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PromotionsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

class PromotionsRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val promotionDao: PromotionDao,
) : PromotionsRepository {

    override suspend fun getPromotions(includeInactive: Boolean): Result<List<Promotion>> {
        if (!networkMonitor.isOnline()) return readCache(includeInactive)
        return try {
            // Upsert complete pages; offset pagination is not a database snapshot when rows
            // change concurrently, so absence from a page must never imply a remote delete.
            val remote = fetchAllPromotions()
            promotionDao.upsertAll(remote.map { it.toEntity() })
            readCache(includeInactive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readCache(includeInactive) else Result.failure(failure)
        }
    }

    override suspend fun getPromotionPage(includeInactive: Boolean, page: PageRequest): Result<DataPage<Promotion>> {
        val scope = "promotions:${if (includeInactive) "all" else "active"}"
        if (!networkMonitor.isOnline()) return readPromotionPage(includeInactive, page, scope)
        return try {
            val rows = supabase.from(PROMOTIONS_TABLE).select(columns = PROMOTION_COLUMNS) {
                if (!includeInactive) filter { eq("activo", true) }
                order("fecha_publicacion", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(page.offset.toLong()..(page.offset + page.limit).toLong())
            }.decodeList<PromotionDto>()
            promotionDao.upsertAll(rows.map { it.toEntity() })
            Result.success(pageFromFetchedWindow(rows.map { it.toEntity().toDomain() }, page, scope))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readPromotionPage(includeInactive, page, scope) else Result.failure(failure)
        }
    }

    override suspend fun createPromotion(
        titulo: String,
        descripcion: String,
        terminosCondiciones: String?,
        imagenUrl: String?,
        fechaVencimiento: String?,
    ): Result<Promotion> {
        validate(titulo, descripcion, null, fechaVencimiento)?.let { return Result.failure(it) }
        if (!networkMonitor.isOnline()) return Result.failure(offlineWriteError())
        return try {
            val request = CreatePromotionRequestDto(
                id = UUID.randomUUID().toString(),
                titulo = titulo.trim(),
                descripcion = descripcion.trim(),
                terminosCondiciones = terminosCondiciones.cleanOrNull(),
                imagenUrl = imagenUrl.cleanOrNull(),
                fechaVencimiento = fechaVencimiento.cleanOrNull(),
            )
            val saved = supabase.from(PROMOTIONS_TABLE).insert(request) {
                select(columns = PROMOTION_COLUMNS)
            }.decodeSingle<PromotionDto>()
            saveToCache(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
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
        validate(titulo, descripcion, id, fechaVencimiento)?.let { return Result.failure(it) }
        if (!networkMonitor.isOnline()) return Result.failure(offlineWriteError())
        return try {
            val request = UpdatePromotionRequestDto(
                titulo = titulo.trim(),
                descripcion = descripcion.trim(),
                terminosCondiciones = terminosCondiciones.cleanOrNull(),
                imagenUrl = imagenUrl.cleanOrNull(),
                fechaVencimiento = fechaVencimiento.cleanOrNull(),
                activo = activo,
            )
            val saved = supabase.from(PROMOTIONS_TABLE).update(request) {
                filter { eq("id", id.trim()) }
                select(columns = PROMOTION_COLUMNS)
            }.decodeSingle<PromotionDto>()
            saveToCache(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun setPromotionActive(id: String, activo: Boolean): Result<Promotion> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("La promoción es obligatoria."))
        if (!networkMonitor.isOnline()) return Result.failure(offlineWriteError())
        return try {
            val updated = supabase.from(PROMOTIONS_TABLE).update(UpdatePromotionActiveDto(activo)) {
                filter { eq("id", id.trim()) }
                select(columns = PROMOTION_COLUMNS)
            }.decodeSingle<PromotionDto>()
            saveToCache(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun saveToCache(dto: PromotionDto): Result<Promotion> {
        val entity = dto.toEntity()
        promotionDao.upsert(entity)
        return Result.success(entity.toDomain())
    }

    private suspend fun readCache(includeInactive: Boolean): Result<List<Promotion>> = try {
        Result.success(promotionDao.getPromotions(includeInactive).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun readPromotionPage(includeInactive: Boolean, page: PageRequest, scope: String): Result<DataPage<Promotion>> = try {
        val rows = promotionDao.getPromotionPage(includeInactive, page.limit + 1, page.offset).map { it.toDomain() }
        Result.success(pageFromFetchedWindow(rows, page, scope, fetchedAt = null))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun fetchAllPromotions(): List<PromotionDto> {
        val rows = mutableListOf<PromotionDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(PROMOTIONS_TABLE).select(columns = PROMOTION_COLUMNS) {
                order("fecha_publicacion", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<PromotionDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private fun validate(titulo: String, descripcion: String, id: String?, fecha: String?): IllegalArgumentException? {
        if (titulo.isBlank()) return IllegalArgumentException("El título de la promoción es obligatorio.")
        if (descripcion.isBlank()) return IllegalArgumentException("La descripción de la promoción es obligatoria.")
        if (id != null && id.isBlank()) return IllegalArgumentException("El identificador de la promoción es obligatorio.")
        if (fecha != null && !DATE_PATTERN.matches(fecha)) {
            return IllegalArgumentException("La fecha de vencimiento debe tener el formato AAAA-MM-DD.")
        }
        return null
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun offlineWriteError() = IllegalStateException(
        "Se requiere conexión para crear o editar promociones; no se guardó ningún cambio.",
    )

    private fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException) return true
            val name = current::class.simpleName.orEmpty()
            if (name.contains("HttpRequestException") || name.contains("ConnectException") ||
                name.contains("UnknownHostException") || name.contains("SocketTimeoutException")
            ) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val SNAPSHOT_PAGE_SIZE = 500L
        const val PROMOTIONS_TABLE = "promociones"
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val PROMOTION_COLUMNS = Columns.list(
            "id, titulo, descripcion, terminos_condiciones, imagen_url, fecha_publicacion, fecha_vencimiento, activo, created_at, updated_at, sync_version",
        )
    }
}
