package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.PayrollDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeePaymentDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterPayrollPaymentRpcParams
import com.mirandadevsource.exquisssitaappmanager.data.sync.EmployeesPayrollSyncCoordinator
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeePayment
import com.mirandadevsource.exquisssitaappmanager.domain.repository.DuplicatePaymentException
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class PayrollRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val payrollDao: PayrollDao,
    private val employeeDao: EmployeeDao,
    private val syncCoordinator: EmployeesPayrollSyncCoordinator,
) : PayrollRepository {
    private val paymentMutex = Mutex()

    override suspend fun getPaymentsForPeriod(periodoInicio: String): Result<List<EmployeePayment>> {
        try {
            requireMonday(periodoInicio)
            if (networkMonitor.isOnline()) {
                syncCoordinator.syncPending()
                val remoteRows = supabase.from(PAYROLL_TABLE)
                    .select(columns = PAYMENT_COLUMNS) {
                        filter { eq("periodo_inicio", periodoInicio) }
                    }
                    .decodeList<EmployeePaymentDto>()
                remoteRows.forEach { payment ->
                    val employeeLocalId = employeeDao.getByRemoteId(payment.empleadoId)?.localId
                    payrollDao.upsertPayment(payment.toEntity(employeeLocalId))
                }
            }
            return Result.success(localPaymentsForPeriod(periodoInicio))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) return Result.success(localPaymentsForPeriod(periodoInicio))
            return Result.failure(failure)
        }
    }

    override suspend fun getPaymentHistoryForEmployee(empleadoId: String): Result<List<EmployeePayment>> {
        if (empleadoId.isBlank()) {
            return Result.failure(IllegalArgumentException("El empleado es obligatorio para consultar su historial."))
        }
        return try {
            val employee = employeeDao.getByLocalId(empleadoId) ?: employeeDao.getByRemoteId(empleadoId)
            val remoteEmployeeId = employee?.remoteId ?: empleadoId
            if (networkMonitor.isOnline()) {
                syncCoordinator.syncPending()
                val remoteRows = supabase.from(PAYROLL_TABLE)
                    .select(columns = PAYMENT_COLUMNS) {
                        filter { eq("empleado_id", remoteEmployeeId) }
                    }
                    .decodeList<EmployeePaymentDto>()
                remoteRows.forEach { payrollDao.upsertPayment(it.toEntity(employee?.localId)) }
            }
            Result.success(localHistoryForEmployee(employee?.localId ?: empleadoId, remoteEmployeeId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                val employee = employeeDao.getByLocalId(empleadoId) ?: employeeDao.getByRemoteId(empleadoId)
                Result.success(localHistoryForEmployee(employee?.localId ?: empleadoId, employee?.remoteId ?: empleadoId))
            } else {
                Result.failure(failure)
            }
        }
    }

    override suspend fun registerWeeklyPayment(
        empleadoId: String,
        periodoInicio: String,
        fechaPago: String?,
        notas: String?,
    ): Result<EmployeePayment> = paymentMutex.withLock {
        try {
            require(empleadoId.isNotBlank()) { "El empleado es obligatorio para registrar el pago." }
            requireMonday(periodoInicio)
            fechaPago?.let(LocalDate::parse)
            val employee = employeeDao.getByLocalId(empleadoId) ?: employeeDao.getByRemoteId(empleadoId)
                ?: throw NoSuchElementException("El empleado no está disponible en el caché local.")
            if (!employee.activo) throw IllegalStateException("No se pueden registrar pagos para un empleado inactivo.")

            if (networkMonitor.isOnline()) {
                syncCoordinator.syncPending()
                val existingIntent = payrollDao.getPaymentIntent(employee.localId, periodoInicio)
                if (existingIntent != null) return@withLock Result.success(existingIntent.toDomain(employee))
                registerRemotePayment(employee, periodoInicio, fechaPago, notas)
            } else {
                queuePaymentIntent(employee, periodoInicio, fechaPago, notas)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun registerRemotePayment(
        employee: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity,
        periodoInicio: String,
        fechaPago: String?,
        notas: String?,
    ): Result<EmployeePayment> {
        val remoteEmployeeId = employee.remoteId
            ?: return queuePaymentIntent(employee, periodoInicio, fechaPago, notas)
        return try {
            val payment = supabase.postgrest.rpc(
                REGISTER_PAYMENT_RPC,
                RegisterPayrollPaymentRpcParams(
                    empleadoId = remoteEmployeeId,
                    periodoInicio = periodoInicio,
                    fechaPago = fechaPago,
                    notas = notas?.trim()?.ifBlank { null },
                ),
            ).decodeAs<EmployeePaymentDto>()
            payrollDao.upsertPayment(payment.toEntity(employee.localId))
            Result.success(payment.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PostgrestRestException) {
            if (failure.code == UNIQUE_VIOLATION) {
                cacheExistingPayment(employee, remoteEmployeeId, periodoInicio)
                Result.failure(DuplicatePaymentException(failure))
            } else if (failure.isNetworkFailure()) {
                queuePaymentIntent(employee, periodoInicio, fechaPago, notas)
            } else {
                Result.failure(failure)
            }
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) queuePaymentIntent(employee, periodoInicio, fechaPago, notas)
            else Result.failure(failure)
        }
    }

    private suspend fun cacheExistingPayment(
        employee: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity,
        remoteEmployeeId: String,
        periodoInicio: String,
    ) {
        val existing = supabase.from(PAYROLL_TABLE)
            .select(columns = PAYMENT_COLUMNS) {
                filter {
                    eq("empleado_id", remoteEmployeeId)
                    eq("periodo_inicio", periodoInicio)
                }
            }
            .decodeList<EmployeePaymentDto>()
            .singleOrNull()
        if (existing != null) payrollDao.upsertPayment(existing.toEntity(employee.localId))
    }

    private suspend fun queuePaymentIntent(
        employee: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity,
        periodoInicio: String,
        fechaPago: String?,
        notas: String?,
    ): Result<EmployeePayment> {
        val confirmed = payrollDao.getPaymentsForPeriod(periodoInicio)
            .any { it.empleadoIdRemote == employee.remoteId }
        if (confirmed) return Result.failure(DuplicatePaymentException())
        val existing = payrollDao.getPaymentIntent(employee.localId, periodoInicio)
        val intent = existing ?: PayrollPaymentIntentEntity(
            intentId = UUID.randomUUID().toString(),
            employeeLocalId = employee.localId,
            periodoInicio = periodoInicio,
            requestedFechaPago = fechaPago,
            notas = notas?.trim()?.ifBlank { null },
        )
        payrollDao.upsertPaymentIntent(intent.copy(lastSyncError = null))
        return Result.success(intent.toDomain(employee))
    }

    private suspend fun localPaymentsForPeriod(periodoInicio: String): List<EmployeePayment> {
        val confirmed = payrollDao.getPaymentsForPeriod(periodoInicio).map { it.toDomain() }
        val pending = payrollDao.getPaymentIntentsForPeriod(periodoInicio).mapNotNull { intent ->
            employeeDao.getByLocalId(intent.employeeLocalId)?.let(intent::toDomain)
        }
        return (confirmed + pending).sortedBy { it.empleadoId }
    }

    private suspend fun localHistoryForEmployee(localId: String, remoteId: String): List<EmployeePayment> {
        val confirmed = payrollDao.getPaymentHistory(remoteId).map { it.toDomain() }
        val pending = payrollDao.getPaymentIntentsForEmployee(localId).mapNotNull { intent ->
            employeeDao.getByLocalId(intent.employeeLocalId)?.let(intent::toDomain)
        }
        return (confirmed + pending).sortedWith(
            compareByDescending<EmployeePayment> { it.periodoInicio.orEmpty() }
                .thenByDescending { it.fechaPago.orEmpty() },
        )
    }

    private fun requireMonday(value: String) {
        val date = LocalDate.parse(value)
        require(date.dayOfWeek.value == 1) { "El periodo semanal debe iniciar en lunes." }
    }

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
        const val PAYROLL_TABLE = "historial_pagos_empleados"
        const val REGISTER_PAYMENT_RPC = "registrar_pago_nomina"
        const val UNIQUE_VIOLATION = "23505"
        val PAYMENT_COLUMNS = Columns.list(
            "id, empleado_id, monto, fecha_pago, notas, periodo_inicio, fecha_programada, registrado_por, created_at, sync_version",
        )
    }
}
