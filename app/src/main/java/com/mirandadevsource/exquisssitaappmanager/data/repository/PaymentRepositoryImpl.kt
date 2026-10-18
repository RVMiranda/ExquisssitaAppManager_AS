package com.mirandadevsource.exquisssitaappmanager.data.repository

import androidx.room.withTransaction
import com.mirandadevsource.exquisssitaappmanager.core.database.AppDatabase
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.*
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentDetailEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentStockEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TransactionEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.*
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.*
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PaymentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class PaymentRepositoryImpl @Inject constructor(
    supabase: SupabaseClient, networkMonitor: NetworkMonitor, commandDao: OrderCommandDao,
    leaseDao: SyncExecutionLeaseDao,
    authRepository: AuthRepository, private val database: AppDatabase,
    private val transactionDao: TransactionDao, private val detailDao: OrderDetailDao,
    private val orderDao: OrderDao, private val productDao: ProductDao,
) : OrderSyncSupport(supabase, networkMonitor, commandDao, authRepository, leaseDao), PaymentRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePayments(orderId: String): Flow<List<Payment>> = authRepository.state.flatMapLatest { state ->
        val actor = (state as? AuthState.SignedIn)?.session?.userId ?: return@flatMapLatest flowOf(emptyList())
        combine(transactionDao.observeForOrder(orderId), commandDao.observePaymentCommands(actor, orderId)) { transactions, commands ->
            val allocations = transactionDao.getPaymentDetails(transactions.map { it.id }).groupBy { it.transaccionId }
            val stock = transactionDao.getPaymentStock(transactions.map { it.id }).groupBy { it.transaccionId }
            val confirmed = transactions.map { tx ->
                Payment(tx.id, tx.ordenId, tx.montoCentavos, PaymentMethod.fromDatabase(tx.metodoPago), tx.fecha, tx.idempotencyKey,
                    PaymentStatus.CONFIRMED, allocations[tx.id].orEmpty().map { line ->
                        PaymentAllocation(line.detalleOrdenId, line.cantidad, line.montoCentavos,
                            stock[tx.id].orEmpty().firstOrNull { it.detalleOrdenId == line.detalleOrdenId }?.cantidad ?: 0)
                    })
            }
            val intents = commands.filter { it.entity == PAYMENT_COMMAND }.mapNotNull { command ->
                runCatching {
                    val obj = Json.parseToJsonElement(command.payloadJson).jsonObject
                    val raw = obj["p_asignaciones"]?.jsonArray.orEmpty()
                    val mapped = raw.map { entry ->
                        val a = entry.jsonObject
                        PaymentAllocation(a.getValue("detalle_orden_id").jsonPrimitive.content,
                            a.getValue("cantidad").jsonPrimitive.int, a.getValue("monto").jsonPrimitive.long,
                            a["cantidad_stock"]?.jsonPrimitive?.int ?: 0)
                    }
                    Payment(command.idempotencyKey, orderId, obj.getValue("p_monto").jsonPrimitive.long,
                        PaymentMethod.fromDatabase(obj.getValue("p_metodo_pago").jsonPrimitive.content),
                        Instant.ofEpochMilli(command.createdAtMillis).toString(), command.idempotencyKey,
                        when (command.status) { OrderCommandEntity.REJECTED -> PaymentStatus.REJECTED; else -> PaymentStatus.PENDING_SYNC },
                        mapped, command.lastErrorCode)
                }.getOrNull()
            }
            (confirmed + intents).sortedBy { it.paidAt }
        }
    }

    override suspend fun processPayment(request: PaymentRequest): Result<PaymentCommand> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa."))
        if (hasRemoteSession() && networkMonitor.isOnline()) {
            try { refreshOrderAndItsPendingCommands(request.orderId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (!failure.isNetworkFailure()) return Result.failure(failure) }
        }
        if (request.amountCents <= 0 || request.allocations.isEmpty() || request.allocations.sumOf { it.amountCents } != request.amountCents) {
            return Result.failure(IllegalArgumentException("El importe y las asignaciones del pago no coinciden."))
        }
        val order = orderDao.getById(request.orderId) ?: return Result.failure(NoSuchElementException("Orden no disponible en el caché."))
        if (order.actorUserId != actor) return Result.failure(IllegalStateException("La orden pertenece a otra sesión local."))
        if (order.estado != OrderStatus.OPEN.databaseValue) return Result.failure(IllegalStateException("La orden no está abierta."))
        if (commandDao.countPendingPayments(actor, request.orderId) > 0) return Result.failure(IllegalStateException("La orden tiene un pago pendiente de confirmación."))
        val total = detailDao.getForOrder(request.orderId).filter { it.estadoPago != PaymentLineStatus.CANCELLED.databaseValue }
            .sumOf { it.cantidad.toLong() * it.precioUnitarioCentavos }
        if (request.amountCents > total - transactionDao.getPaidAmount(request.orderId)) return Result.failure(IllegalArgumentException("El pago supera el saldo pendiente de la orden."))
        if (request.allocations.map { it.orderDetailId }.distinct().size != request.allocations.size) return Result.failure(IllegalArgumentException("No se permiten asignaciones duplicadas para una partida."))
        val lines = detailDao.getForOrder(request.orderId).associateBy { it.id }
        for (allocation in request.allocations) {
            val line = lines[allocation.orderDetailId] ?: return Result.failure(IllegalArgumentException("La asignación no pertenece a la orden."))
            if (allocation.quantity <= 0 || allocation.quantity > line.cantidad || allocation.amountCents < 0 || allocation.stockQuantity < 0 || allocation.stockQuantity > allocation.quantity ||
                allocation.amountCents > allocation.quantity.toLong() * line.precioUnitarioCentavos ||
                (allocation.amountCents == 0L && allocation.stockQuantity == 0)) return Result.failure(IllegalArgumentException("Cantidad o importe de asignación inválido."))
            val product = productDao.getById(line.productoId) ?: return Result.failure(IllegalStateException("Sin catálogo local para validar el inventario."))
            if (allocation.stockQuantity > 0 && (!product.activo || !product.controlaInventario)) return Result.failure(IllegalStateException("La partida no puede generar descuento de inventario."))
            if (allocation.stockQuantity > 0 && (product.stockActual ?: 0) < allocation.stockQuantity) return Result.failure(IllegalStateException("El stock local no alcanza para este pago."))
        }
        val key = UUID.randomUUID().toString()
        val allocJson = buildJsonArray {
            request.allocations.forEach { allocation ->
                add(buildJsonObject {
                    put("detalle_orden_id", allocation.orderDetailId); put("cantidad", allocation.quantity)
                    put("monto", allocation.amountCents); put("cantidad_stock", allocation.stockQuantity)
                    if (allocation.stockQuantity > 0) put("stock_idempotency_key", UUID.randomUUID().toString())
                })
            }
        }
        val payload = RegisterPaymentPayload(request.orderId, request.amountCents, request.method.databaseValue, key, allocJson)
        val command = OrderCommandEntity(key, actor, request.orderId, PAYMENT_COMMAND, request.orderId, "INSERT",
            Json.encodeToString(RegisterPaymentPayload.serializer(), payload), 0)
        return try {
            database.withTransaction { commandDao.insert(command) }
            val earlierMutations = commandDao.getRecoverableForOrder(actor, request.orderId)
                .any { it.idempotencyKey != key && it.entity != PAYMENT_COMMAND }
            val result = if (earlierMutations) command else submit(command)
            if (result.status == OrderCommandEntity.SYNCED) refreshConfirmedPayments(request.orderId)
            val body = result.resultJson?.let { Json.decodeFromString<SyncCommandResponse>(it) }
            Result.success(PaymentCommand(key, request.orderId,
                if (result.status == OrderCommandEntity.REJECTED) PaymentStatus.REJECTED else if (result.status == OrderCommandEntity.SYNCED) PaymentStatus.CONFIRMED else PaymentStatus.PENDING_SYNC,
                body?.transactionId, result.lastErrorCode ?: body?.reason))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }

    override suspend fun syncPendingPayments(): Result<Unit> {
        val paymentSyncResult = syncActorCommands(includePayments = true)
        if (paymentSyncResult.isFailure) return paymentSyncResult
        val actor = actorId() ?: return Result.success(Unit)
        return try {
            // Refresh each cached open order after its immutable payment intent has been replayed.
            orderDao.getAllForActor(actor).map { it.id }.forEach { refreshConfirmedPayments(it) }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }

    private suspend fun refreshConfirmedPayments(orderId: String) {
        if (!hasRemoteSession()) return
        val transactions = supabase.from(TRANSACTIONS).select(columns = TRANSACTION_COLUMNS) { filter { eq("orden_id", orderId) } }.decodeList<TransactionDto>()
        if (transactions.isEmpty()) return
        val ids = transactions.map { it.id }
        val details = supabase.from(PAYMENT_DETAILS).select(columns = PAYMENT_DETAIL_COLUMNS) { filter { isIn("transaccion_id", ids) } }.decodeList<PaymentDetailDto>()
        val stock = supabase.from(PAYMENT_STOCK).select(columns = PAYMENT_STOCK_COLUMNS) { filter { isIn("transaccion_id", ids) } }.decodeList<PaymentStockDto>()
        val orderRows = supabase.from(ORDERS).select(columns = ORDER_COLUMNS) { filter { eq("id", orderId) } }.decodeList<OrderDto>()
        val detailRows = supabase.from(ORDER_DETAILS).select(columns = DETAIL_COLUMNS) { filter { eq("orden_id", orderId) } }.decodeList<OrderDetailDto>()
        database.withTransaction {
            transactionDao.upsertTransactions(transactions.map { TransactionEntity(it.id, it.ordenId, it.monto, it.metodoPago, it.fecha, it.idempotencyKey, it.createdAt, it.syncVersion) })
            transactionDao.upsertPaymentDetails(details.map { PaymentDetailEntity(it.id, it.transaccionId, it.detalleOrdenId, it.cantidad, it.monto) })
            transactionDao.upsertPaymentStock(stock.map { PaymentStockEntity(it.id, it.transaccionId, it.detalleOrdenId, it.productoId, it.cantidad, it.movimientoId, it.idempotencyKey, it.createdAt) })
            val actor = actorId().orEmpty()
            orderRows.firstOrNull()?.let { row ->
                if (commandDao.countPendingForEntity(actor, row.id) == 0) orderDao.upsert(row.toEntity(actor))
            }
            detailRows.forEach { row ->
                if (commandDao.countPendingForEntity(actor, row.id) == 0) detailDao.upsert(row.toEntity())
            }
        }
    }

    private suspend fun refreshOrderAndItsPendingCommands(orderId: String) {
        syncActorCommands()
        // Populate the current order snapshot before accepting an online payment.
        val actor = actorId() ?: return
        val rows = supabase.from(ORDERS).select(columns = ORDER_COLUMNS) { filter { eq("id", orderId) } }.decodeList<OrderDto>()
        val details = supabase.from(ORDER_DETAILS).select(columns = DETAIL_COLUMNS) { filter { eq("orden_id", orderId) } }.decodeList<OrderDetailDto>()
        database.withTransaction {
            rows.firstOrNull()?.let { row -> if (commandDao.countPendingForEntity(actor, row.id) == 0) orderDao.upsert(row.toEntity(actor)) }
            details.forEach { row -> if (commandDao.countPendingForEntity(actor, row.id) == 0) detailDao.upsert(row.toEntity()) }
        }
    }

    private companion object {
        const val PAYMENT_COMMAND = "registrar_pago"; const val TRANSACTIONS = "transacciones"
        const val PAYMENT_DETAILS = "pago_detalles"; const val PAYMENT_STOCK = "pago_detalle_stock"
        const val ORDERS = "ordenes"; const val ORDER_DETAILS = "detalle_orden"
        val TRANSACTION_COLUMNS = Columns.list("id, orden_id, monto, metodo_pago, fecha, idempotency_key, created_at, sync_version")
        val PAYMENT_DETAIL_COLUMNS = Columns.list("id, transaccion_id, detalle_orden_id, cantidad, monto")
        val PAYMENT_STOCK_COLUMNS = Columns.list("id, transaccion_id, detalle_orden_id, producto_id, cantidad, movimiento_id, idempotency_key, created_at")
        val ORDER_COLUMNS = Columns.list("id, mesa_id, cliente_id, tipo_servicio, estado, fecha_apertura, fecha_cierre, notas, created_at, updated_at, sync_version")
        val DETAIL_COLUMNS = Columns.list("id, orden_id, producto_id, cantidad, precio_unitario, estado_pago, notas, created_at, updated_at, sync_version")
    }
}
