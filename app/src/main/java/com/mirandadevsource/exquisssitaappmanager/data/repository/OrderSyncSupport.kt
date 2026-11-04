package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncCommandResponse
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncExecutePayload
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.CancelDetailPayload
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.sync.SyncExecutionMutex
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import java.io.IOException

abstract class OrderSyncSupport(
    protected val supabase: SupabaseClient,
    protected val networkMonitor: NetworkMonitor,
    protected val commandDao: OrderCommandDao,
    protected val authRepository: AuthRepository,
    protected val leaseDao: SyncExecutionLeaseDao,
) {

    protected fun actorId(): String? = (authRepository.state.value as? AuthState.SignedIn)?.session?.userId

    protected fun hasRemoteSession(): Boolean {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session ?: return false
        return !session.isOffline && supabase.auth.currentUserOrNull()?.id == session.userId
    }

    protected suspend fun submit(command: OrderCommandEntity): OrderCommandEntity {
        val actor = actorId() ?: return command
        if (actor != command.actorUserId || !networkMonitor.isOnline() || !hasRemoteSession()) return command
        val remoteActor = supabase.auth.currentUserOrNull()?.id
        if (remoteActor != actor) return command
        var outcome = command
        val acquired = SyncExecutionMutex.withActorLease(actor, leaseDao) {
            val latest = commandDao.getByKey(command.idempotencyKey) ?: return@withActorLease
            if (latest.status == OrderCommandEntity.SYNCED || latest.status == OrderCommandEntity.REJECTED) {
                outcome = latest
            } else if (blockOnEarlierCommand(latest)) {
                outcome = commandDao.getByKey(latest.idempotencyKey) ?: latest
            } else if (latest.nextAttemptAtMillis == null || latest.nextAttemptAtMillis <= System.currentTimeMillis()) {
                outcome = submitClaimed(latest)
            } else {
                outcome = latest
            }
        }
        return if (acquired) outcome else commandDao.getByKey(command.idempotencyKey) ?: command
    }

    private suspend fun submitClaimed(command: OrderCommandEntity): OrderCommandEntity {
        val actor = actorId()
        if (actor != command.actorUserId || !networkMonitor.isOnline() || !hasRemoteSession()) return command
        val inProgress = command.copy(status = OrderCommandEntity.PROCESSING, attemptCount = command.attemptCount + 1, updatedAtMillis = System.currentTimeMillis())
        commandDao.update(inProgress)
        return try {
            val response = if (command.entity == "cancelar_detalle_orden") {
                supabase.postgrest.rpc("cancelar_detalle_orden", CancelDetailPayload(command.entityId, command.idempotencyKey))
                    .decodeAs<SyncCommandResponse>()
            } else {
                supabase.postgrest.rpc("sync_execute_v2", SyncExecutePayload(
                    entity = command.entity,
                    entityId = command.entityId,
                    operation = command.operation,
                    payload = Json.parseToJsonElement(command.payloadJson).jsonObject,
                    key = command.idempotencyKey,
                    expectedVersion = command.expectedVersion ?: 0,
                )).decodeAs<SyncCommandResponse>()
            }
            when (response.status.lowercase()) {
                "confirmed" -> {
                    val updated = inProgress.copy(status = OrderCommandEntity.SYNCED, resultJson = Json.encodeToString(SyncCommandResponse.serializer(), response), updatedAtMillis = System.currentTimeMillis())
                    commandDao.update(updated)
                    updated
                }
                "rejected" -> {
                    val updated = inProgress.copy(status = OrderCommandEntity.REJECTED, lastErrorCode = response.reason ?: "rejected", resultJson = Json.encodeToString(SyncCommandResponse.serializer(), response), updatedAtMillis = System.currentTimeMillis())
                    commandDao.update(updated)
                    updated
                }
                else -> throw IllegalStateException("Respuesta de sincronización no reconocida.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val remoteFailure = failure is PostgrestRestException
            val updated = inProgress.copy(
                status = if (remoteFailure) OrderCommandEntity.REJECTED else OrderCommandEntity.PENDING,
                lastErrorCode = failure.message?.take(120) ?: "network_unknown_result",
                nextAttemptAtMillis = if (remoteFailure) null else System.currentTimeMillis() + retryDelay(inProgress.attemptCount),
                updatedAtMillis = System.currentTimeMillis(),
            )
            commandDao.update(updated)
            updated
        }
    }

    protected suspend fun syncActorCommands(includePayments: Boolean = false, force: Boolean = false): Result<Unit> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa para sincronizar."))
        if (!networkMonitor.isOnline() || !hasRemoteSession()) {
            return if (force) Result.failure(IllegalStateException("No hay conexión con el servidor para sincronizar."))
            else Result.success(Unit)
        }
        return try {
            var failedCount = 0
            var lastErrorMessage: String? = null
            val acquired = SyncExecutionMutex.withActorLease(actor, leaseDao) {
                if (force) {
                    commandDao.resetInterruptedProcessing(actor)
                    commandDao.clearBackoffForActor(actor)
                    commandDao.markBlockedCommandsAsRejected(actor)
                }
                val pendingList = commandDao.getRecoverableForActor(actor, if (force) Long.MAX_VALUE else System.currentTimeMillis())
                    .filter { includePayments || it.entity != "registrar_pago" }

                for (pending in pendingList) {
                    val latest = commandDao.getByKey(pending.idempotencyKey) ?: continue
                    if (latest.status != OrderCommandEntity.SYNCED && latest.status != OrderCommandEntity.REJECTED &&
                        (force || latest.nextAttemptAtMillis == null || latest.nextAttemptAtMillis <= System.currentTimeMillis())
                    ) {
                        if (blockOnEarlierCommand(latest)) {
                            failedCount++
                            lastErrorMessage = "Comando bloqueado por operación previa no resuelta."
                        } else {
                            val outcome = submitClaimed(latest)
                            if (outcome.status != OrderCommandEntity.SYNCED) {
                                failedCount++
                                lastErrorMessage = outcome.lastErrorCode
                            }
                        }
                    }
                }
            }
            if (!acquired) {
                Result.failure(IllegalStateException("La sincronización de esta cuenta ya está activa en otra ejecución."))
            } else if (failedCount > 0) {
                Result.failure(IllegalStateException(lastErrorMessage ?: "No se pudieron sincronizar $failedCount operaciones."))
            } else {
                Result.success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /**
     * Enforces FIFO within a table/order aggregate. A rejected predecessor permanently
     * blocks later intents until the user resolves the aggregate; a retryable predecessor
     * simply holds later intents until it confirms.
     */
    private suspend fun blockOnEarlierCommand(command: OrderCommandEntity): Boolean {
        val earlier = commandDao.getEarlierUnresolvedForAggregate(
            actorId = command.actorUserId,
            aggregateId = command.aggregateId,
            createdAtMillis = command.createdAtMillis,
            key = command.idempotencyKey,
        )
        if (earlier.isEmpty()) return false
        val rejected = earlier.firstOrNull { it.status == OrderCommandEntity.REJECTED }
        if (rejected != null && command.status != OrderCommandEntity.REJECTED) {
            commandDao.update(command.copy(
                status = OrderCommandEntity.REJECTED,
                lastErrorCode = "BLOCKED_BY_REJECTED_COMMAND:${rejected.idempotencyKey}",
                updatedAtMillis = System.currentTimeMillis(),
            ))
        }
        return true
    }

    protected fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException) return true
            val name = current::class.simpleName.orEmpty()
            if (name.contains("HttpRequestException") || name.contains("ConnectException") || name.contains("UnknownHostException") || name.contains("SocketTimeoutException")) return true
            current = current.cause
        }
        return false
    }

    private fun retryDelay(attempt: Int) = (15_000L * (1L shl attempt.coerceAtMost(6))).coerceAtMost(15 * 60_000L)
}
