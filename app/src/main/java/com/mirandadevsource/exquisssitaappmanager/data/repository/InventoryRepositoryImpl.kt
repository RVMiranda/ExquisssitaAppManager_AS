package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.InventoryCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ProductDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.StockMovementDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.InventoryCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.CreateProductRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.InventoryRpcResponseDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.InventoryRpcSixParams
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.InventoryRpcVersionedParams
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.ProductDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.StockMovementDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.UpdateProductRequestDto
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.sync.SyncExecutionMutex
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromFetchedWindow
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryCommandStatus
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryFailureCode
import com.mirandadevsource.exquisssitaappmanager.domain.entity.InventoryMovementType
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RegisterInventoryMovement
import com.mirandadevsource.exquisssitaappmanager.domain.entity.StockMovement
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.InventoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class InventoryRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val commandDao: InventoryCommandDao,
    private val authRepository: AuthRepository,
    private val leaseDao: SyncExecutionLeaseDao,
) : InventoryRepository {

    override suspend fun getProducts(includeInactive: Boolean): Result<List<Product>> {
        if (!networkMonitor.isOnline()) return readProductCache(includeInactive)
        syncPendingCommands()
        return try {
            // Download inactive rows too, so soft-deactivation propagates to local reads.
            val remote = fetchAllProducts()
            productDao.upsertAll(remote.map { it.toEntity() })
            readProductCache(includeInactive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readProductCache(includeInactive)
            else Result.failure(failure)
        }
    }

    override suspend fun getProductPage(includeInactive: Boolean, page: PageRequest): Result<DataPage<Product>> {
        val scope = "products:${if (includeInactive) "all" else "active"}"
        if (!networkMonitor.isOnline()) return readProductPage(includeInactive, page, scope)
        return try {
            val rows = supabase.from(PRODUCTS_TABLE).select(columns = PRODUCT_COLUMNS) {
                if (!includeInactive) filter { eq("activo", true) }
                order("categoria", Order.ASCENDING)
                order("nombre", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(page.offset.toLong()..(page.offset + page.limit).toLong())
            }.decodeList<ProductDto>()
            productDao.upsertAll(rows.map { it.toEntity() })
            Result.success(pageFromFetchedWindow(rows.map { it.toEntity().toDomain() }, page, scope))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readProductPage(includeInactive, page, scope) else Result.failure(failure)
        }
    }

    override suspend fun createProduct(
        nombre: String,
        precioCentavos: Long,
        categoria: String,
        controlaInventario: Boolean,
        stockMinimo: Int,
    ): Result<Product> {
        if (nombre.isBlank()) return Result.failure(IllegalArgumentException("El nombre del producto es obligatorio."))
        if (precioCentavos < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo."))
        if (categoria.isBlank()) return Result.failure(IllegalArgumentException("La categoría es obligatoria."))
        if (controlaInventario && stockMinimo < 0) return Result.failure(IllegalArgumentException("El stock mínimo no puede ser negativo."))

        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Crear productos requiere conexión para sincronizar con el catálogo."))
        }

        return try {
            val newId = java.util.UUID.randomUUID().toString()
            val request = CreateProductRequestDto(
                id = newId,
                nombre = nombre.trim(),
                precio = precioCentavos,
                categoria = categoria.trim(),
                controlaInventario = controlaInventario,
                stockMinimo = if (controlaInventario) stockMinimo else 0,
                activo = true,
            )
            val remote = supabase.from(PRODUCTS_TABLE).insert(request) {
                select(columns = PRODUCT_COLUMNS)
            }.decodeSingle<ProductDto>()

            val entity = remote.toEntity()
            productDao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun updateProduct(
        id: String,
        nombre: String,
        precioCentavos: Long,
        categoria: String,
        controlaInventario: Boolean,
        stockMinimo: Int,
        activo: Boolean,
    ): Result<Product> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("El identificador del producto es obligatorio."))
        if (nombre.isBlank()) return Result.failure(IllegalArgumentException("El nombre del producto es obligatorio."))
        if (precioCentavos < 0) return Result.failure(IllegalArgumentException("El precio no puede ser negativo."))
        if (categoria.isBlank()) return Result.failure(IllegalArgumentException("La categoría es obligatoria."))
        if (controlaInventario && stockMinimo < 0) return Result.failure(IllegalArgumentException("El stock mínimo no puede ser negativo."))

        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Editar productos requiere conexión para sincronizar con el catálogo."))
        }

        return try {
            val request = UpdateProductRequestDto(
                nombre = nombre.trim(),
                precio = precioCentavos,
                categoria = categoria.trim(),
                controlaInventario = controlaInventario,
                stockMinimo = if (controlaInventario) stockMinimo else 0,
                activo = activo,
            )
            val remote = supabase.from(PRODUCTS_TABLE).update(request) {
                filter { eq("id", id.trim()) }
                select(columns = PRODUCT_COLUMNS)
            }.decodeSingle<ProductDto>()

            val entity = remote.toEntity()
            productDao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun getMovements(
        productId: String,
        offset: Int,
        limit: Int,
    ): Result<List<StockMovement>> {
        if (productId.isBlank()) return Result.failure(IllegalArgumentException("El producto es obligatorio."))
        if (offset < 0 || limit !in 1..MAX_MOVEMENT_PAGE_SIZE) {
            return Result.failure(IllegalArgumentException("El rango de movimientos no es válido."))
        }
        if (!networkMonitor.isOnline()) return readMovementCache(productId, offset, limit)
        syncPendingCommands()

        return try {
            val remoteRows = supabase.from(MOVEMENTS_TABLE)
                .select(columns = MOVEMENT_COLUMNS) {
                    filter { eq("producto_id", productId) }
                    order(column = "created_at", order = Order.DESCENDING)
                    order(column = "id", order = Order.DESCENDING)
                    val first = offset.toLong()
                    range(first..(first + limit - 1L))
                }
                .decodeList<StockMovementDto>()
            stockMovementDao.upsertAll(remoteRows.map { it.toEntity() })
            Result.success(remoteRows.map { it.toEntity().toDomain() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readMovementCache(productId, offset, limit)
            else Result.failure(failure)
        }
    }

    override suspend fun registerMovement(request: RegisterInventoryMovement): Result<InventoryCommand> {
        val session = authRepository.state.value as? AuthState.SignedIn
            ?: return Result.failure(IllegalStateException("Se requiere una sesión para registrar inventario."))
        val key = java.util.UUID.randomUUID().toString()
        val local = request.toEntity(actorUserId = session.session.userId, idempotencyKey = key)

        return try {
            commandDao.insert(local)
            val saved = commandDao.getByKey(key)
                ?: throw IllegalStateException("El comando no quedó guardado en la cola local.")
            if (networkMonitor.isOnline() && !session.session.isOffline) {
                syncPendingCommands()
            }
            Result.success(commandDao.getByKey(key)?.toDomain() ?: saved.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun syncPendingCommands(): Result<Unit> {
        if (!networkMonitor.isOnline()) return Result.success(Unit)
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(IllegalStateException("Se requiere iniciar sesión para sincronizar inventario."))
        val currentAuthUserId = supabase.auth.currentUserOrNull()?.id
        if (session.isOffline || currentAuthUserId != session.userId) {
            return Result.failure(IllegalStateException("La sesión remota no está disponible para sincronizar inventario."))
        }

        return try {
            val acquired = SyncExecutionMutex.withActorLease(session.userId, leaseDao) {
                val commands = commandDao.getRecoverableCommandsForActor(
                    session.userId,
                    System.currentTimeMillis(),
                )
                for (command in commands) {
                    if (command.status == InventoryCommandStatus.CONFIRMED_AWAITING_REFRESH.name) {
                        refreshConfirmedCommand(command)
                    } else {
                        submitCommand(command)
                    }
                }
            }
            if (acquired) Result.success(Unit)
            else Result.failure(IllegalStateException("La sincronización de esta cuenta ya está activa en otra ejecución."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInventoryCommands(): Flow<List<InventoryCommand>> =
        authRepository.state.flatMapLatest { state ->
            val userId = (state as? AuthState.SignedIn)?.session?.userId
            if (userId == null) flowOf(emptyList()) else commandDao.observeUnresolvedCommands(userId)
        }.map { rows -> rows.map { it.toDomain() } }

    private suspend fun submitCommand(command: InventoryCommandEntity) {
        val processing = command.copy(
            status = InventoryCommandStatus.PROCESSING.name,
            attemptCount = command.attemptCount + 1,
            updatedAtMillis = System.currentTimeMillis(),
            lastErrorCode = null,
            nextAttemptAtMillis = null,
        )
        commandDao.update(processing)

        try {
            val response = if (command.type == InventoryMovementType.AJUSTE.databaseValue) {
                val expectedVersion = command.expectedVersion
                    ?: throw IllegalArgumentException("El ajuste requiere una versión de stock.")
                supabase.postgrest.rpc(
                    REGISTER_MOVEMENT_RPC,
                    InventoryRpcVersionedParams(
                        productId = command.productId,
                        quantity = command.quantity,
                        type = command.type,
                        idempotencyKey = command.idempotencyKey,
                        referenceId = command.referenceId,
                        notes = command.notes,
                        expectedVersion = expectedVersion,
                    ),
                ).decodeAs<InventoryRpcResponseDto>()
            } else {
                supabase.postgrest.rpc(
                    REGISTER_MOVEMENT_RPC,
                    InventoryRpcSixParams(
                        productId = command.productId,
                        quantity = command.quantity,
                        type = command.type,
                        idempotencyKey = command.idempotencyKey,
                        referenceId = command.referenceId,
                        notes = command.notes,
                    ),
                ).decodeAs<InventoryRpcResponseDto>()
            }

            when (response.status.lowercase()) {
                "confirmed" -> {
                    val movementId = response.movementId
                        ?: throw IllegalStateException("La confirmación no incluyó el movimiento remoto.")
                    val productVersion = response.productVersion
                        ?: throw IllegalStateException("La confirmación no incluyó la versión del producto.")
                    val confirmed = processing.copy(
                        status = InventoryCommandStatus.CONFIRMED_AWAITING_REFRESH.name,
                        movementRemoteId = movementId,
                        productVersion = productVersion,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    commandDao.update(confirmed)
                    refreshConfirmedCommand(confirmed)
                }
                "rejected" -> {
                    val code = InventoryFailureCode.fromServer(response.reason)
                    commandDao.update(
                        processing.copy(
                            status = InventoryCommandStatus.REJECTED.name,
                            lastErrorCode = code.name,
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                else -> throw IllegalStateException("La respuesta de inventario no contiene un estado reconocido.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val explicitRemoteError = failure as? PostgrestRestException
            val errorCode = explicitRemoteError?.let { mapRemoteError(it) }
            val current = commandDao.getByKey(command.idempotencyKey) ?: processing
            commandDao.update(
                current.copy(
                    status = if (explicitRemoteError != null) {
                        InventoryCommandStatus.REJECTED.name
                    } else {
                        InventoryCommandStatus.PENDING.name
                    },
                    lastErrorCode = errorCode?.name ?: InventoryFailureCode.NETWORK_UNKNOWN_RESULT.name,
                    updatedAtMillis = System.currentTimeMillis(),
                    nextAttemptAtMillis = if (explicitRemoteError == null) {
                        System.currentTimeMillis() + retryDelayMillis(current.attemptCount)
                    } else {
                        null
                    },
                ),
            )
        }
    }

    private suspend fun refreshConfirmedCommand(command: InventoryCommandEntity) {
        val movementId = command.movementRemoteId ?: return
        try {
            val movement = supabase.from(MOVEMENTS_TABLE)
                .select(columns = MOVEMENT_COLUMNS) { filter { eq("id", movementId) } }
                .decodeList<StockMovementDto>()
                .singleOrNull() ?: throw NoSuchElementException("No se encontró el movimiento confirmado.")
            val product = supabase.from(PRODUCTS_TABLE)
                .select(columns = PRODUCT_COLUMNS) { filter { eq("id", command.productId) } }
                .decodeList<ProductDto>()
                .singleOrNull() ?: throw NoSuchElementException("No se encontró el producto confirmado.")

            stockMovementDao.upsertAll(listOf(movement.toEntity()))
            productDao.upsertAll(listOf(product.toEntity()))
            commandDao.update(
                command.copy(
                    status = InventoryCommandStatus.SYNCED.name,
                    lastErrorCode = null,
                    nextAttemptAtMillis = null,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The RPC may already have committed. Keep its receipt and only retry the read reconciliation.
            commandDao.update(
                command.copy(
                    status = InventoryCommandStatus.CONFIRMED_AWAITING_REFRESH.name,
                    lastErrorCode = InventoryFailureCode.NETWORK_UNKNOWN_RESULT.name,
                    updatedAtMillis = System.currentTimeMillis(),
                    nextAttemptAtMillis = System.currentTimeMillis() + retryDelayMillis(command.attemptCount),
                ),
            )
        }
    }

    private suspend fun readProductCache(includeInactive: Boolean): Result<List<Product>> = try {
        Result.success(productDao.getProducts(includeInactive).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun readProductPage(includeInactive: Boolean, page: PageRequest, scope: String): Result<DataPage<Product>> = try {
        val rows = productDao.getProductPage(includeInactive, page.limit + 1, page.offset).map { it.toDomain() }
        Result.success(pageFromFetchedWindow(rows, page, scope, fetchedAt = null))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun fetchAllProducts(): List<ProductDto> {
        val rows = mutableListOf<ProductDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(PRODUCTS_TABLE).select(columns = PRODUCT_COLUMNS) {
                order("id", Order.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<ProductDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private suspend fun readMovementCache(
        productId: String,
        offset: Int,
        limit: Int,
    ): Result<List<StockMovement>> = try {
        Result.success(stockMovementDao.getForProduct(productId, limit, offset).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun mapRemoteError(error: PostgrestRestException): InventoryFailureCode {
        val message = error.message.orEmpty().lowercase()
        val code = error.code.orEmpty().lowercase()
        val knownReason = listOf(
            "not_authorized", "invalid_command", "version_required", "product_unavailable",
            "stock_version_conflict", "insufficient_stock", "invalid_compensation",
            "compensation_exceeds_original", "idempotency_mismatch", "legacy_movement_requires_review",
        ).firstOrNull { message.contains(it) }
        return InventoryFailureCode.fromServer(knownReason ?: code)
    }

    private fun retryDelayMillis(attemptCount: Int): Long =
        (15_000L * (1L shl (attemptCount.coerceAtMost(6)))).coerceAtMost(15 * 60_000L)

    private fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException) return true
            val typeName = current::class.simpleName.orEmpty()
            if (typeName.contains("HttpRequestException") || typeName.contains("ConnectException") ||
                typeName.contains("UnknownHostException") || typeName.contains("SocketTimeoutException")
            ) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val SNAPSHOT_PAGE_SIZE = 500L
        const val PRODUCTS_TABLE = "productos"
        const val MOVEMENTS_TABLE = "movimientos_inventario"
        const val REGISTER_MOVEMENT_RPC = "registrar_movimiento_inventario"
        const val MAX_MOVEMENT_PAGE_SIZE = 200
        val PRODUCT_COLUMNS = Columns.list(
            "id, nombre, precio, categoria, controla_inventario, stock_actual, stock_minimo, activo, created_at, updated_at, sync_version",
        )
        val MOVEMENT_COLUMNS = Columns.list(
            "id, producto_id, cantidad, tipo, referencia_id, notas, creado_por, created_at, idempotency_key, sync_version",
        )
    }
}
