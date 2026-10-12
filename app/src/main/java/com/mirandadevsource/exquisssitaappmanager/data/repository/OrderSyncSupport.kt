package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncCommandResponse
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncExecutePayload
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.CancelDetailPayload
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import java.io.IOException

abstract class OrderSyncSupport(
    protected val supabase: SupabaseClient,
    protected val networkMonitor: NetworkMonitor,
    protected val commandDao: OrderCommandDao,
    protected val authRepository: AuthRepository,
) {
    private val syncMutex = Mutex()

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

    protected suspend fun syncActorCommands(includePayments: Boolean = false): Result<Unit> {
        val actor = actorId() ?: return Result.failure(IllegalStateException("Se requiere una sesión activa para sincronizar."))
        if (!networkMonitor.isOnline() || !hasRemoteSession()) return Result.success(Unit)
        return try {
            syncMutex.withLock {
                commandDao.getRecoverableForActor(actor, System.currentTimeMillis())
                    .filter { includePayments || it.entity != "registrar_pago" }
                    .forEach { submit(it) }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
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
