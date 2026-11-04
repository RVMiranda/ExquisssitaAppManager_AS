package com.mirandadevsource.exquisssitaappmanager.data.repository

import androidx.room.withTransaction
import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TableDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TableEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.TableDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.OrderDto
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.TableRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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
class TableRepositoryImpl @Inject constructor(
    supabase: SupabaseClient,
    networkMonitor: NetworkMonitor,
    commandDao: OrderCommandDao,
    leaseDao: SyncExecutionLeaseDao,
    authRepository: AuthRepository,
    private val database: AppDatabase,
    private val tableDao: TableDao,
    private val orderDao: OrderDao,
) : OrderSyncSupport(supabase, networkMonitor, commandDao, authRepository, leaseDao), TableRepository {

    override fun observeTables(): Flow<List<Table>> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(emptyList())
        tableDao.observeTableSummaries(actor).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun refreshTables(): Result<Unit> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa."))
        if (!networkMonitor.isOnline() || !hasRemoteSession()) return Result.success(Unit)
        return try {
            syncActorCommands()
            val remote = fetchAllTables()
            val remoteOrders = fetchAllOrders()
            val remoteIds = remote.mapTo(hashSetOf()) { it.id }
            database.withTransaction {
                tableDao.getAll().filter { it.actorUserId == actor && it.syncState == TableEntity.PENDING && it.id !in remoteIds && commandDao.countPendingForEntity(actor, it.id) == 0 }
                    .forEach { tableDao.deleteById(it.id) }
                remote.forEach { dto ->
                    val existing = tableDao.getById(dto.id)
                    if (existing == null || commandDao.countPendingForEntity(actor, dto.id) == 0) {
                        tableDao.upsert(dto.toEntity())
                    }
                }
                remoteOrders.forEach { dto ->
                    if (commandDao.countPendingForEntity(actor, dto.id) == 0) orderDao.upsert(dto.toEntity(actor))
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) Result.success(Unit) else Result.failure(failure)
        }
    }

    override suspend fun createTable(number: String): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión en línea para crear mesas."))
        val normalized = number.trim()
        if (normalized.isBlank()) return Result.failure(IllegalArgumentException("El número de mesa es obligatorio."))
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        return persistTableCommand(actor, id, "INSERT", 0L, buildJsonObject {
            put("numero_mesa", normalized); put("estado", "Libre")
        }.toString(), TableEntity(id, normalized, "Libre", now, now, 0, TableEntity.PENDING, actorUserId = actor))
    }

    override suspend fun setReservation(tableId: String, reserved: Boolean): Result<String> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión en línea para cambiar una reserva."))
        if (networkMonitor.isOnline() && hasRemoteSession()) refreshTables()
        val table = tableDao.getById(tableId) ?: return Result.failure(NoSuchElementException("La mesa no está en el caché local."))
        if (table.syncState == TableEntity.PENDING && table.actorUserId != actor) return Result.failure(IllegalStateException("La mesa tiene una creación pendiente de otra sesión."))
        if (commandDao.countPendingForEntity(actor, tableId) > 0) return Result.failure(IllegalStateException("La mesa tiene un cambio pendiente de sincronización."))
        if (!reserved && tableDao.countOpenOrders(tableId) > 0) return Result.failure(IllegalStateException("La mesa tiene órdenes abiertas y no se puede liberar."))
        if (table.estado.equals("Ocupada", true) && reserved) return Result.failure(IllegalStateException("La ocupación se deriva de las órdenes abiertas."))
        val status = if (reserved) "Reservada" else "Libre"
        val payload = buildJsonObject { put("estado", status) }.toString()
        return persistTableCommand(actor, tableId, "UPDATE", table.syncVersion, payload, table.copy(estado = status, syncState = TableEntity.PENDING, actorUserId = actor))
    }

    override suspend fun syncPendingCommands(): Result<Unit> = syncPendingCommands(force = false)

    override suspend fun syncPendingCommands(force: Boolean): Result<Unit> {
        val result = syncActorCommands(force = force)
        if (result.isSuccess && hasRemoteSession()) return refreshTables()
        return result
    }

    private suspend fun persistTableCommand(actor: String, id: String, operation: String, version: Long, payload: String, optimistic: TableEntity): Result<String> = try {
        val command = OrderCommandEntity(UUID.randomUUID().toString(), actor, id, TABLES, id, operation, payload, version)
        database.withTransaction { commandDao.insert(command); tableDao.upsert(optimistic) }
        val submitted = submit(command)
        if (submitted.status == OrderCommandEntity.SYNCED || submitted.status == OrderCommandEntity.REJECTED) refreshTables()
        Result.success(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun fetchAllTables(): List<TableDto> {
        val rows = mutableListOf<TableDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(TABLES).select(columns = TABLE_COLUMNS) {
                order("id", Order.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<TableDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private suspend fun fetchAllOrders(): List<OrderDto> {
        val rows = mutableListOf<OrderDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(ORDERS).select(columns = ORDER_COLUMNS) {
                order("id", Order.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<OrderDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private companion object {
        const val SNAPSHOT_PAGE_SIZE = 500L
        const val TABLES = "mesas"
        const val ORDERS = "ordenes"
        val TABLE_COLUMNS = Columns.list("id, numero_mesa, estado, created_at, updated_at, sync_version")
        val ORDER_COLUMNS = Columns.list("id, mesa_id, cliente_id, tipo_servicio, estado, fecha_apertura, fecha_cierre, notas, created_at, updated_at, sync_version")
    }
}
