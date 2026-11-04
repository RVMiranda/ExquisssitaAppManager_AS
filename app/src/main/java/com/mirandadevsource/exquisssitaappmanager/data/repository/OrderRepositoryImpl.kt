package com.mirandadevsource.exquisssitaappmanager.data.repository

import androidx.room.withTransaction
import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.*
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.*
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.OrderDetailDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.OrderDto
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.*
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order as PostgrestOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class OrderRepositoryImpl @Inject constructor(
    supabase: SupabaseClient, networkMonitor: NetworkMonitor, commandDao: OrderCommandDao,
    leaseDao: SyncExecutionLeaseDao,
    authRepository: AuthRepository, private val database: AppDatabase,
    private val orderDao: OrderDao, private val detailDao: OrderDetailDao,
    private val tableDao: TableDao, private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
) : OrderSyncSupport(supabase, networkMonitor, commandDao, authRepository, leaseDao), OrderRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOrders(): Flow<List<Order>> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(emptyList())
        orderDao.observeAll(actor).map { rows -> rows.map { it.toDomain() } }
    }
    override fun observeOrdersForTable(tableId: String): Flow<List<Order>> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(emptyList())
        orderDao.observeForTable(tableId, actor).map { rows -> rows.map { it.toDomain() } }
    }
    override fun observeOrder(orderId: String): Flow<Order?> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(null)
        orderDao.observeById(orderId, actor).map { it?.toDomain() }
    }
    override fun observeOrderDetails(orderId: String): Flow<List<OrderDetail>> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(emptyList())
        detailDao.observeForOrder(orderId, actor).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun refreshOrders(): Result<Unit> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa."))
        if (!networkMonitor.isOnline() || !hasRemoteSession()) return Result.success(Unit)
        return try {
            syncActorCommands()
            // The Data API may cap each response. Build complete snapshots before touching Room;
            // a partial page must never make valid cached rows look deleted.
            val orders = fetchAllOrders()
            val details = fetchAllOrderDetails()
            database.withTransaction {
                // A missing row in an offset-paginated response is not an explicit delete.
                // Deletes/cancellations must be reconciled by their command or server status.
                orders.forEach { dto -> if (commandDao.countPendingForEntity(actor, dto.id) == 0) orderDao.upsert(dto.toEntity(actor)) }
                details.forEach { dto -> if (commandDao.countPendingForEntity(actor, dto.id) == 0) detailDao.upsert(dto.toEntity()) }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (failure.isNetworkFailure()) Result.success(Unit) else Result.failure(failure) }
    }

    override suspend fun getOrderPage(filter: OrderPageFilter, page: PageRequest): Result<DataPage<Order>> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar órdenes."))
        val actor = session.userId
        val from = filter.fromInclusive?.toString()
        val to = filter.toExclusive?.toString()
        val scope = filter.scope()
        if (!networkMonitor.isOnline() || session.isOffline) {
            return readOrderPage(actor, filter, page, scope)
        }
        if (!hasRemoteSession()) {
            return Result.failure(SecurityException("La sesión remota no coincide con el usuario activo."))
        }
        return try {
            val remote = supabase.from(ORDERS).select(columns = ORDER_COLUMNS) {
                filter {
                    if (filter.openOnly) eq("estado", OrderStatus.OPEN.databaseValue)
                    from?.let { gte("fecha_apertura", it) }
                    to?.let { lt("fecha_apertura", it) }
                }
                order("fecha_apertura", PostgrestOrder.DESCENDING)
                order("id", PostgrestOrder.DESCENDING)
                range(page.offset.toLong()..(page.offset + page.limit).toLong())
            }.decodeList<OrderDto>()
            if (actorId() != actor || !hasRemoteSession()) {
                return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a cargar las órdenes."))
            }
            database.withTransaction {
                remote.forEach { dto ->
                    val existing = orderDao.getById(dto.id)
                    // The current Room primary key is the remote id, not (id, actor).
                    // Never overwrite another actor's row; that account can still use the
                    // authoritative response for this request, but it won't be cached here.
                    if (existing == null || (existing.syncState == OrderEntity.SYNCED &&
                            commandDao.countPendingForEntity(actor, dto.id) == 0)
                    ) {
                        orderDao.upsert(dto.toEntity(actor))
                    }
                }
            }
            val visibleRows = remote.map { dto ->
                val existing = orderDao.getById(dto.id)
                existing?.takeIf { it.actorUserId == actor && it.syncState != OrderEntity.SYNCED }
                    ?.toDomain()
                    ?: dto.toEntity(actor).toDomain()
            }
            Result.success(pageFromFetchedWindow(visibleRows, page, scope))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readOrderPage(actor, filter, page, scope) else Result.failure(failure)
        }
    }

    private suspend fun readOrderPage(
        actor: String,
        filter: OrderPageFilter,
        page: PageRequest,
        scope: String,
    ): Result<DataPage<Order>> = try {
        val rows = orderDao.getPage(
            actorId = actor,
            openOnly = filter.openOnly,
            fromInclusive = filter.fromInclusive?.toString(),
            toExclusive = filter.toExclusive?.toString(),
            limit = page.limit + 1,
            offset = page.offset,
        )
        val oldestFetch = rows.minOfOrNull { it.cachedAtMillis }?.let(Instant::ofEpochMilli)
        Result.success(pageFromFetchedWindow(rows.map { it.toDomain() }, page, scope, oldestFetch))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    override suspend fun openOrder(request: OpenOrderRequest): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere sesión activa."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshOrders()
        if (request.serviceType == ServiceType.TABLE && request.tableId == null) return Result.failure(IllegalArgumentException("Una orden de mesa requiere mesa."))
        if (request.tableId != null) {
            val table = tableDao.getById(request.tableId) ?: return Result.failure(IllegalArgumentException("Mesa no disponible en caché."))
            if (table.syncState == TableEntity.PENDING || commandDao.countPendingForEntity(actor, request.tableId) > 0) {
                return Result.failure(IllegalStateException("La mesa tiene cambios pendientes; sincronízala antes de abrir una orden."))
            }
        }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        val payload = buildJsonObject {
            put("mesa_id", request.tableId); put("cliente_id", request.customerId); put("tipo_servicio", request.serviceType.databaseValue)
            put("estado", OrderStatus.OPEN.databaseValue); put("fecha_apertura", now); put("notas", request.notes?.trim()?.ifBlank { null })
        }.toString()
        val order = OrderEntity(id, request.tableId, request.customerId, request.serviceType.databaseValue, OrderStatus.OPEN.databaseValue, now, null, request.notes, now, now, 0, actor, OrderEntity.PENDING)
        return enqueueAndSave(actor, id, ORDERS, id, "INSERT", payload, 0) {
            request.tableId?.let { tableId ->
                val latestTable = tableDao.getById(tableId)
                if (latestTable == null || latestTable.syncState == TableEntity.PENDING ||
                    commandDao.countPendingForEntity(actor, tableId) > 0
                ) {
                    throw IllegalStateException("La mesa cambió y tiene sincronización pendiente; no se abrió la orden.")
                }
            }
            orderDao.upsert(order)
        }
    }

    override suspend fun addItem(request: AddOrderItemRequest): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere sesión activa."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshOrders()
        if (request.quantity <= 0 || request.unitPriceCents < 0) return Result.failure(IllegalArgumentException("Cantidad o precio inválido."))
        val order = orderDao.getById(request.orderId) ?: return Result.failure(NoSuchElementException("Orden no disponible en caché."))
        if (order.syncState != OrderEntity.SYNCED && order.actorUserId != actor) return Result.failure(IllegalStateException("La orden pertenece a otra sesión local no sincronizada."))
        if (order.estado != OrderStatus.OPEN.databaseValue) return Result.failure(IllegalStateException("La orden no está abierta."))
        val product = productDao.getById(request.productId) ?: return Result.failure(NoSuchElementException("Producto no disponible en caché."))
        if (!product.activo) return Result.failure(IllegalStateException("El producto está desactivado; sincroniza el catálogo."))
        if (!product.precioAbierto && product.precioCentavos != request.unitPriceCents) {
            return Result.failure(IllegalStateException("El producto cambió o su precio no coincide; sincroniza el catálogo."))
        }
        if (product.precioAbierto && request.unitPriceCents <= 0) {
            return Result.failure(IllegalArgumentException("El importe para un producto de precio abierto debe ser mayor a cero."))
        }
        val id = UUID.randomUUID().toString(); val now = Instant.now().toString()
        val payload = buildJsonObject {
            put("orden_id", request.orderId); put("producto_id", request.productId); put("cantidad", request.quantity)
            put("precio_unitario", request.unitPriceCents)
            put("notas", request.notes?.trim()?.ifBlank { null })
        }.toString()
        val line = OrderDetailEntity(id, request.orderId, request.productId, request.quantity, request.unitPriceCents, PaymentLineStatus.PENDING.databaseValue, request.notes, now, now, 0, OrderDetailEntity.PENDING)
        return enqueueAndSave(actor, request.orderId, DETAILS, id, "INSERT", payload, 0) { detailDao.upsert(line) }
    }

    override suspend fun updateItemQuantity(detailId: String, quantity: Int): Result<String> {
        if (quantity <= 0) return Result.failure(IllegalArgumentException("La cantidad debe ser mayor que cero."))
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere sesión activa."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshOrders()
        val line = detailDao.getById(detailId) ?: return Result.failure(NoSuchElementException("Partida no encontrada."))
        val parentOrder = orderDao.getById(line.ordenId)
        if (parentOrder != null && parentOrder.syncState != OrderEntity.SYNCED && parentOrder.actorUserId != actor) return Result.failure(IllegalStateException("La partida pertenece a otra sesión local no sincronizada."))
        if (commandDao.countPendingForEntity(actor, detailId) > 0) return Result.failure(IllegalStateException("La partida tiene un cambio pendiente de sincronización."))
        if (line.estadoPago != PaymentLineStatus.PENDING.databaseValue || transactionDao.countAllocationsForDetail(detailId) > 0) return Result.failure(IllegalStateException("No se puede cambiar una partida con pagos."))
        val changed = line.copy(cantidad = quantity, syncState = OrderDetailEntity.PENDING)
        return enqueueAndSave(actor, line.ordenId, DETAILS, detailId, "UPDATE", buildJsonObject { put("cantidad", quantity) }.toString(), line.syncVersion) { detailDao.upsert(changed) }
    }

    override suspend fun cancelItem(detailId: String): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere sesión activa."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshOrders()
        val line = detailDao.getById(detailId) ?: return Result.failure(NoSuchElementException("Partida no encontrada."))
        val parentOrder = orderDao.getById(line.ordenId)
        if (parentOrder != null && parentOrder.syncState != OrderEntity.SYNCED && parentOrder.actorUserId != actor) return Result.failure(IllegalStateException("La partida pertenece a otra sesión local no sincronizada."))
        if (commandDao.countPendingForEntity(actor, detailId) > 0) return Result.failure(IllegalStateException("La partida tiene un cambio pendiente de sincronización."))
        if (line.estadoPago != PaymentLineStatus.PENDING.databaseValue || transactionDao.countAllocationsForDetail(detailId) > 0) return Result.failure(IllegalStateException("Una partida con pagos no puede cancelarse sin regla de reembolso."))
        val key = UUID.randomUUID().toString()
        val payload = buildJsonObject { put("detalle_orden_id", detailId); put("p_idempotency_key", key) }.toString()
        val changed = line.copy(estadoPago = PaymentLineStatus.CANCELLED.databaseValue, syncState = OrderDetailEntity.PENDING)
        return enqueueAndSave(actor, line.ordenId, "cancelar_detalle_orden", detailId, "INSERT", payload, line.syncVersion, key) { detailDao.upsert(changed) }
    }

    override suspend fun cancelOrder(orderId: String): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere sesión activa."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshOrders()
        val order = orderDao.getById(orderId) ?: return Result.failure(NoSuchElementException("Orden no encontrada."))
        if (order.syncState != OrderEntity.SYNCED && order.actorUserId != actor) return Result.failure(IllegalStateException("La orden pertenece a otra sesión local no sincronizada."))
        if (commandDao.countPendingForEntity(actor, orderId) > 0) return Result.failure(IllegalStateException("La orden tiene un cambio pendiente de sincronización."))
        if (order.estado != OrderStatus.OPEN.databaseValue || transactionDao.getForOrder(orderId).isNotEmpty()) return Result.failure(IllegalStateException("Una orden con pagos requiere definir el reembolso antes de cancelarla."))
        val closedAt = Instant.now().toString(); val changed = order.copy(estado = OrderStatus.CANCELLED.databaseValue, fechaCierre = closedAt, syncState = OrderEntity.PENDING)
        val payload = buildJsonObject { put("estado", OrderStatus.CANCELLED.databaseValue); put("fecha_cierre", closedAt) }.toString()
        return enqueueAndSave(actor, orderId, ORDERS, orderId, "UPDATE", payload, order.syncVersion) { orderDao.upsert(changed) }
    }

    override suspend fun syncPendingCommands(): Result<Unit> = syncPendingCommands(force = false)

    override suspend fun syncPendingCommands(force: Boolean): Result<Unit> {
        val result = syncActorCommands(force = force)
        if (result.isSuccess && hasRemoteSession()) return refreshOrders()
        return result
    }

    override suspend fun cleanBlockedCommands(): Result<Int> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa."))
        return try {
            val count = commandDao.deleteBlockedOrRejectedCommands(actor)
            Result.success(count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun enqueueAndSave(actor: String, aggregate: String, entity: String, entityId: String, op: String, payload: String, version: Long, key: String = UUID.randomUUID().toString(), save: suspend () -> Unit): Result<String> {
        val command = OrderCommandEntity(key, actor, aggregate, entity, entityId, op, payload, version)
        return try {
            database.withTransaction { commandDao.insert(command); save() }
            val submitted = submit(command)
            if (submitted.status == OrderCommandEntity.SYNCED || submitted.status == OrderCommandEntity.REJECTED) refreshOrders()
            Result.success(entityId)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }

    private suspend fun fetchAllOrders(): List<OrderDto> {
        val rows = mutableListOf<OrderDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(ORDERS).select(columns = ORDER_COLUMNS) {
                order("id", PostgrestOrder.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<OrderDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private suspend fun fetchAllOrderDetails(): List<OrderDetailDto> {
        val rows = mutableListOf<OrderDetailDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(DETAILS).select(columns = DETAIL_COLUMNS) {
                order("id", PostgrestOrder.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<OrderDetailDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private companion object {
        const val SNAPSHOT_PAGE_SIZE = 500L
        const val ORDERS = "ordenes"; const val DETAILS = "detalle_orden"
        val ORDER_COLUMNS = Columns.list("id, mesa_id, cliente_id, tipo_servicio, estado, fecha_apertura, fecha_cierre, notas, created_at, updated_at, sync_version")
        val DETAIL_COLUMNS = Columns.list("id, orden_id, producto_id, cantidad, precio_unitario, estado_pago, notas, created_at, updated_at, sync_version")
    }
}
