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
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.DuplicatePaymentException
import com.mirandadevsource.exquisssitaappmanager.domain.repository.PayrollRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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
    private val authRepository: AuthRepository,
) : PayrollRepository {
    private val paymentMutex = Mutex()

    override suspend fun getPaymentsForPeriod(periodoInicio: String): Result<List<EmployeePayment>> {
        try {
            requireMonday(periodoInicio)
            val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar nómina."))
            val actor = session.userId
            if (canUseRemote(session)) {
                syncCoordinator.syncPending()
                val remoteRows = fetchPaymentsForPeriod(periodoInicio)
                remoteRows.forEach { payment ->
                    val employeeLocalId = employeeDao.getByRemoteId(payment.empleadoId, actor)?.localId
                    payrollDao.upsertPayment(payment.toEntity(employeeLocalId, actor))
                }
            }
            return Result.success(localPaymentsForPeriod(periodoInicio, actor))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val actor = currentSession()?.userId ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar nómina."))
            if (failure.isNetworkFailure()) return Result.success(localPaymentsForPeriod(periodoInicio, actor))
            return Result.failure(failure)
        }
    }

    override suspend fun getPaymentHistoryForEmployee(empleadoId: String): Result<List<EmployeePayment>> {
        if (empleadoId.isBlank()) {
            return Result.failure(IllegalArgumentException("El empleado es obligatorio para consultar su historial."))
        }
        return try {
            val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar nómina."))
            val actor = session.userId
            val employee = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
                ?: employeeDao.getByRemoteId(empleadoId, actor)
            val remoteEmployeeId = employee?.remoteId ?: empleadoId
            if (canUseRemote(session)) {
                syncCoordinator.syncPending()
                val remoteRows = fetchPaymentHistory(remoteEmployeeId)
                remoteRows.forEach { payrollDao.upsertPayment(it.toEntity(employee?.localId, actor)) }
            }
            Result.success(localHistoryForEmployee(employee?.localId ?: empleadoId, remoteEmployeeId, actor))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                val actor = currentSession()?.userId ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar nómina."))
                val employee = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
                    ?: employeeDao.getByRemoteId(empleadoId, actor)
                Result.success(localHistoryForEmployee(employee?.localId ?: empleadoId, employee?.remoteId ?: empleadoId, actor))
            } else {
                Result.failure(failure)
            }
        }
    }

    override suspend fun getPaymentHistoryPage(empleadoId: String, page: PageRequest): Result<DataPage<EmployeePayment>> {
        if (empleadoId.isBlank()) return Result.failure(IllegalArgumentException("El empleado es obligatorio para consultar su historial."))
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar nómina."))
        val actor = session.userId
        val employee = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
            ?: employeeDao.getByRemoteId(empleadoId, actor)
        val remoteEmployeeId = employee?.remoteId ?: empleadoId
        val scope = "payroll-history"
        return try {
            val remoteAvailable = canUseRemote(session)
            val rows = if (remoteAvailable) {
                val remote = fetchPaymentHistoryPage(remoteEmployeeId, page)
                if (!canUseRemote(session)) return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a cargar el historial."))
                remote.forEach { payrollDao.upsertPayment(it.toEntity(employee?.localId, actor)) }
                remote.map { it.toDomain() }
            } else {
                payrollDao.getPaymentHistoryPage(remoteEmployeeId, actor, page.limit + 1, page.offset)
                    .map { it.toDomain() }
            }
            val hasMore = rows.size > page.limit
            val items = rows.take(page.limit)
            Result.success(DataPage(
                items = items,
                nextOffset = if (hasMore) page.offset + items.size else null,
                hasMore = hasMore,
                scope = scope,
                fetchedAt = if (remoteAvailable) java.time.Instant.now() else null,
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                val cached = payrollDao.getPaymentHistoryPage(remoteEmployeeId, actor, page.limit + 1, page.offset).map { it.toDomain() }
                val hasMore = cached.size > page.limit
                val items = cached.take(page.limit)
                Result.success(DataPage(items, if (hasMore) page.offset + items.size else null, hasMore, scope, null))
            } else Result.failure(failure)
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
            val session = currentSession() ?: throw SecurityException("Se requiere una sesión activa para registrar nómina.")
            val actor = session.userId
            val employee = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
                ?: employeeDao.getByRemoteId(empleadoId, actor)
                ?: throw NoSuchElementException("El empleado no está disponible en el caché local.")
            if (!employee.activo) throw IllegalStateException("No se pueden registrar pagos para un empleado inactivo.")

            if (canUseRemote(session)) {
                syncCoordinator.syncPending()
                val existingIntent = payrollDao.getPaymentIntent(employee.localId, periodoInicio)
                if (existingIntent != null) {
                    if (existingIntent.actorUserId != actor) throw SecurityException("Existe un pago pendiente creado por otra cuenta; no se puede adoptar ni reenviar desde esta sesión.")
                    return@withLock Result.success(existingIntent.toDomain(employee))
                }
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
            val actor = currentActor() ?: throw SecurityException("La sesión expiró antes de guardar la nómina.")
            if (actor != employee.cacheActorUserId || supabase.auth.currentUserOrNull()?.id != actor) {
                throw SecurityException("La sesión cambió durante el registro; la respuesta no se asignó a otra cuenta.")
            }
            payrollDao.upsertPayment(payment.toEntity(employee.localId, actor))
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
        if (existing != null) {
            val actor = currentActor() ?: return
            if (actor != employee.cacheActorUserId) return
            payrollDao.upsertPayment(existing.toEntity(employee.localId, actor))
        }
    }

    private suspend fun queuePaymentIntent(
        employee: com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity,
        periodoInicio: String,
        fechaPago: String?,
        notas: String?,
    ): Result<EmployeePayment> {
        val actor = currentActor() ?: return Result.failure(SecurityException("Se requiere una sesión activa para registrar nómina."))
        val confirmed = payrollDao.getPaymentsForPeriod(periodoInicio, actor)
            .any { it.empleadoIdRemote == employee.remoteId }
        if (confirmed) return Result.failure(DuplicatePaymentException())
        val existing = payrollDao.getPaymentIntent(employee.localId, periodoInicio)
        if (existing != null && existing.actorUserId != actor) {
            return Result.failure(SecurityException("Existe un pago pendiente de otra cuenta; debe revisarse sin reasignarlo."))
        }
        val intent = existing ?: PayrollPaymentIntentEntity(
            intentId = UUID.randomUUID().toString(),
            employeeLocalId = employee.localId,
            periodoInicio = periodoInicio,
            requestedFechaPago = fechaPago,
            notas = notas?.trim()?.ifBlank { null },
            actorUserId = actor,
        )
        payrollDao.upsertPaymentIntent(intent.copy(lastSyncError = null))
        return Result.success(intent.toDomain(employee))
    }

    private suspend fun localPaymentsForPeriod(periodoInicio: String, actor: String): List<EmployeePayment> {
        val confirmed = payrollDao.getPaymentsForPeriod(periodoInicio, actor).map { it.toDomain() }
        val pending = payrollDao.getPaymentIntentsForPeriod(periodoInicio, actor).mapNotNull { intent ->
            employeeDao.getByLocalId(intent.employeeLocalId)
                ?.takeIf { it.cacheActorUserId == actor }
                ?.let(intent::toDomain)
        }
        return (confirmed + pending).sortedBy { it.empleadoId }
    }

    private suspend fun fetchPaymentsForPeriod(periodoInicio: String): List<EmployeePaymentDto> {
        val rows = mutableListOf<EmployeePaymentDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(PAYROLL_TABLE).select(columns = PAYMENT_COLUMNS) {
                filter { eq("periodo_inicio", periodoInicio) }
                order("fecha_pago", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<EmployeePaymentDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private suspend fun fetchPaymentHistory(employeeId: String): List<EmployeePaymentDto> {
        val rows = mutableListOf<EmployeePaymentDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(PAYROLL_TABLE).select(columns = PAYMENT_COLUMNS) {
                filter { eq("empleado_id", employeeId) }
                order("fecha_pago", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<EmployeePaymentDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private suspend fun fetchPaymentHistoryPage(employeeId: String, page: PageRequest): List<EmployeePaymentDto> =
        supabase.from(PAYROLL_TABLE).select(columns = PAYMENT_COLUMNS) {
            filter { eq("empleado_id", employeeId) }
            order("fecha_pago", Order.DESCENDING)
            order("id", Order.DESCENDING)
            range(page.offset.toLong()..(page.offset + page.limit).toLong())
        }.decodeList<EmployeePaymentDto>()

    private suspend fun localHistoryForEmployee(localId: String, remoteId: String, actor: String): List<EmployeePayment> {
        val confirmed = payrollDao.getPaymentHistory(remoteId, actor).map { it.toDomain() }
        val pending = payrollDao.getPaymentIntentsForEmployee(localId, actor).mapNotNull { intent ->
            employeeDao.getByLocalId(intent.employeeLocalId)
                ?.takeIf { it.cacheActorUserId == actor }
                ?.let(intent::toDomain)
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

    private fun currentSession(): AppUserSession? = (authRepository.state.value as? AuthState.SignedIn)?.session

    private fun currentActor(): String? = currentSession()?.userId

    private fun canUseRemote(session: AppUserSession): Boolean =
        !session.isOffline && networkMonitor.isOnline() && supabase.auth.currentUserOrNull()?.id == session.userId

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
        const val PAYROLL_TABLE = "historial_pagos_empleados"
        const val REGISTER_PAYMENT_RPC = "registrar_pago_nomina"
        const val UNIQUE_VIOLATION = "23505"
        val PAYMENT_COLUMNS = Columns.list(
            "id, empleado_id, monto, fecha_pago, notas, periodo_inicio, fecha_programada, registrado_por, created_at, sync_version",
        )
    }
}
