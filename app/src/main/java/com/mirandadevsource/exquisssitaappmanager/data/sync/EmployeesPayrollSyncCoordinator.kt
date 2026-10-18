package com.mirandadevsource.exquisssitaappmanager.data.sync

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toConflictSnapshotJson
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toUpsertDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeePaymentDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterPayrollPaymentRpcParams
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Retries local employee edits before payment intents; confirmed payroll is only created by the RPC. */
@Singleton
class EmployeesPayrollSyncCoordinator @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val employeeDao: EmployeeDao,
    private val payrollDao: PayrollDao,
    private val authRepository: AuthRepository,
    private val leaseDao: SyncExecutionLeaseDao,
) {
    suspend fun syncPending() {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session ?: return
        if (session.isOffline || !networkMonitor.isOnline()) return
        if (supabase.auth.currentUserOrNull()?.id != session.userId) return
        SyncExecutionMutex.withActorLease(session.userId, leaseDao) {
            syncEmployeeUpdates(session.userId)
            syncPaymentIntents(session.userId)
        }
    }

    private suspend fun syncEmployeeUpdates(actorUserId: String) {
        employeeDao.getPendingUpdates(actorUserId).forEach { pending ->
            if (!isActorActive(actorUserId)) return
            val remoteId = pending.remoteId ?: return@forEach
            if (pending.updateActorUserId != actorUserId) return@forEach
            try {
                val rows = supabase.from(EMPLOYEES_TABLE).update(pending.toUpsertDto()) {
                    filter {
                        eq("id", remoteId)
                        eq("sync_version", pending.syncVersion)
                    }
                    select(columns = EMPLOYEE_COLUMNS)
                }.decodeList<EmployeeDto>()
                val serverEmployee = rows.singleOrNull()
                if (serverEmployee == null) {
                    val latestServerEmployee = fetchConflictSnapshot(remoteId, actorUserId)
                    employeeDao.upsert(
                        pending.copy(
                            syncState = EmployeeEntity.CONFLICT,
                            lastSyncError = VERSION_CONFLICT,
                            conflictServerJson = latestServerEmployee?.toConflictSnapshotJson(),
                        ),
                    )
                    return@forEach
                }
                employeeDao.upsert(
                    serverEmployee.toEntity(localId = pending.localId, cacheActorUserId = actorUserId).copy(
                        lastSyncedAtMillis = System.currentTimeMillis(),
                        updateActorUserId = null,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                employeeDao.upsert(
                    pending.copy(
                        syncState = EmployeeEntity.SYNC_ERROR,
                        lastSyncError = failure::class.simpleName ?: "SyncError",
                    ),
                )
            }
        }
    }

    private suspend fun fetchConflictSnapshot(remoteId: String, actorUserId: String): EmployeeDto? {
        if (!isActorActive(actorUserId)) return null
        return try {
            supabase.from(EMPLOYEES_TABLE).select(columns = EMPLOYEE_COLUMNS) {
                filter { eq("id", remoteId) }
            }.decodeList<EmployeeDto>().singleOrNull()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun syncPaymentIntents(actorUserId: String) {
        payrollDao.getPendingPaymentIntents(actorUserId).forEach { intent ->
            if (!isActorActive(actorUserId)) return
            if (intent.actorUserId != actorUserId) return@forEach
            val employee = employeeDao.getByLocalId(intent.employeeLocalId)
            val remoteEmployeeId = employee?.takeIf { it.cacheActorUserId == actorUserId }?.remoteId
            if (remoteEmployeeId == null) {
                payrollDao.setPaymentIntentError(intent.intentId, "EMPLOYEE_NOT_SYNCED")
                return@forEach
            }

            try {
                val response = supabase.postgrest.rpc(
                    REGISTER_PAYMENT_RPC,
                    RegisterPayrollPaymentRpcParams(
                        empleadoId = remoteEmployeeId,
                        periodoInicio = intent.periodoInicio,
                        fechaPago = intent.requestedFechaPago,
                        notas = intent.notas,
                    ),
                ).decodeAs<EmployeePaymentDto>()
                payrollDao.upsertPayment(response.toEntity(employeeLocalId = employee.localId, cacheActorUserId = actorUserId))
                payrollDao.deletePaymentIntent(intent.intentId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PostgrestRestException) {
                if (failure.code == UNIQUE_VIOLATION) {
                    try {
                        if (resolveAlreadyRegistered(intent, employee.localId, remoteEmployeeId, actorUserId)) return@forEach
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (lookupFailure: Exception) {
                        payrollDao.setPaymentIntentError(
                            intent.intentId,
                            lookupFailure::class.simpleName ?: "DUPLICATE_LOOKUP_ERROR",
                        )
                        return@forEach
                    }
                }
                payrollDao.setPaymentIntentError(intent.intentId, failure.code ?: "REMOTE_ERROR")
            } catch (failure: Exception) {
                payrollDao.setPaymentIntentError(
                    intent.intentId,
                    failure::class.simpleName ?: "SYNC_ERROR",
                )
            }
        }
    }

    private fun isActorActive(actorUserId: String): Boolean {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session ?: return false
        return session.userId == actorUserId && !session.isOffline && networkMonitor.isOnline() &&
            supabase.auth.currentUserOrNull()?.id == actorUserId
    }

    private suspend fun resolveAlreadyRegistered(
        intent: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity,
        employeeLocalId: String,
        remoteEmployeeId: String,
        actorUserId: String,
    ): Boolean {
        val existing = supabase.from(PAYROLL_TABLE)
            .select(columns = PAYMENT_COLUMNS) {
                filter {
                    eq("empleado_id", remoteEmployeeId)
                    eq("periodo_inicio", intent.periodoInicio)
                }
            }
            .decodeList<EmployeePaymentDto>()
            .singleOrNull() ?: return false
        payrollDao.upsertPayment(existing.toEntity(employeeLocalId, actorUserId))
        payrollDao.deletePaymentIntent(intent.intentId)
        return true
    }

    private companion object {
        const val EMPLOYEES_TABLE = "empleados"
        const val PAYROLL_TABLE = "historial_pagos_empleados"
        const val REGISTER_PAYMENT_RPC = "registrar_pago_nomina"
        const val UNIQUE_VIOLATION = "23505"
        const val VERSION_CONFLICT = "VERSION_CONFLICT"
        val EMPLOYEE_COLUMNS = Columns.list(
            "id, user_id, nombre, apellido, telefono, salario, dia_pago, activo, created_at, updated_at, sync_version",
        )
        val PAYMENT_COLUMNS = Columns.list(
            "id, empleado_id, monto, fecha_pago, notas, periodo_inicio, fecha_programada, registrado_por, created_at, sync_version",
        )
    }
}
