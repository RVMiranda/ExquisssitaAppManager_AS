package com.mirandadevsource.exquisssitaappmanager.data.sync

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toUpsertDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeePaymentDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterPayrollPaymentRpcParams
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Retries local employee edits before payment intents; confirmed payroll is only created by the RPC. */
@Singleton
class EmployeesPayrollSyncCoordinator @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val employeeDao: EmployeeDao,
    private val payrollDao: PayrollDao,
) {
    private val syncMutex = Mutex()

    suspend fun syncPending() = syncMutex.withLock {
        if (!networkMonitor.isOnline()) return@withLock
        syncEmployeeUpdates()
        syncPaymentIntents()
    }

    private suspend fun syncEmployeeUpdates() {
        employeeDao.getPendingUpdates().forEach { pending ->
            val remoteId = pending.remoteId ?: return@forEach
            try {
                val rows = supabase.from(EMPLOYEES_TABLE).update(pending.toUpsertDto()) {
                    filter { eq("id", remoteId) }
                    select(columns = EMPLOYEE_COLUMNS)
                }.decodeList<EmployeeDto>()
                val serverEmployee = rows.singleOrNull() ?: error("No row returned")
                employeeDao.upsert(
                    serverEmployee.toEntity(localId = pending.localId).copy(
                        lastSyncedAtMillis = System.currentTimeMillis(),
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

    private suspend fun syncPaymentIntents() {
        payrollDao.getPendingPaymentIntents().forEach { intent ->
            val employee = employeeDao.getByLocalId(intent.employeeLocalId)
            val remoteEmployeeId = employee?.remoteId
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
                payrollDao.upsertPayment(response.toEntity(employeeLocalId = employee.localId))
                payrollDao.deletePaymentIntent(intent.intentId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PostgrestRestException) {
                if (failure.code == UNIQUE_VIOLATION) {
                    try {
                        if (resolveAlreadyRegistered(intent, employee.localId, remoteEmployeeId)) return@forEach
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

    private suspend fun resolveAlreadyRegistered(
        intent: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity,
        employeeLocalId: String,
        remoteEmployeeId: String,
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
        payrollDao.upsertPayment(existing.toEntity(employeeLocalId))
        payrollDao.deletePaymentIntent(intent.intentId)
        return true
    }

    private companion object {
        const val EMPLOYEES_TABLE = "empleados"
        const val PAYROLL_TABLE = "historial_pagos_empleados"
        const val REGISTER_PAYMENT_RPC = "registrar_pago_nomina"
        const val UNIQUE_VIOLATION = "23505"
        val EMPLOYEE_COLUMNS = Columns.list(
            "id, user_id, nombre, apellido, telefono, salario, dia_pago, activo, created_at, updated_at, sync_version",
        )
        val PAYMENT_COLUMNS = Columns.list(
            "id, empleado_id, monto, fecha_pago, notas, periodo_inicio, fecha_programada, registrado_por, created_at, sync_version",
        )
    }
}
