package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toConflictSnapshotJson
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toUpsertDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpsertEmployeeRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.sync.EmployeesPayrollSyncCoordinator
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromFetchedWindow
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

class EmployeeRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val employeeDao: EmployeeDao,
    private val syncCoordinator: EmployeesPayrollSyncCoordinator,
    private val authRepository: AuthRepository,
) : EmployeeRepository {

    override suspend fun getEmployees(includeInactive: Boolean): Result<List<Employee>> {
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar empleados."))
        val actor = session.userId
        if (!canUseRemote(session)) return cachedEmployees(actor, includeInactive)
        return try {
            syncCoordinator.syncPending()
            val remoteRows = fetchAllEmployees(includeInactive)
            if (!canUseRemote(session)) return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a sincronizar."))
            remoteRows.forEach { remote ->
                val cached = employeeDao.getByRemoteId(remote.id, actor)
                if (cached == null || cached.syncState == EmployeeEntity.SYNCED) {
                    employeeDao.upsert(remote.toEntity(localId = cached?.localId ?: scopedEmployeeId(actor, remote.id), cacheActorUserId = actor))
                }
            }
            cachedEmployees(actor, includeInactive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) cachedEmployees(actor, includeInactive)
            else Result.failure(failure)
        }
    }

    override suspend fun getEmployeePage(includeInactive: Boolean, page: PageRequest): Result<DataPage<Employee>> {
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar empleados."))
        val actor = session.userId
        val scope = "employees:${if (includeInactive) "all" else "active"}"
        if (!canUseRemote(session)) return readEmployeePage(actor, includeInactive, page, scope)
        return try {
            syncCoordinator.syncPending()
            val rows = supabase.from(EMPLOYEES_TABLE).select(columns = EMPLOYEE_COLUMNS) {
                if (!includeInactive) filter { eq("activo", true) }
                order("nombre", Order.ASCENDING)
                order("apellido", Order.ASCENDING)
                order("id", Order.ASCENDING)
                range(page.offset.toLong()..(page.offset + page.limit).toLong())
            }.decodeList<EmployeeDto>()
            if (!canUseRemote(session)) return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a cargar la lista."))
            val localRows = rows.map { dto ->
                val cached = employeeDao.getByRemoteId(dto.id, actor)
                if (cached == null || cached.syncState == EmployeeEntity.SYNCED) {
                    val entity = dto.toEntity(localId = cached?.localId ?: scopedEmployeeId(actor, dto.id), cacheActorUserId = actor)
                    employeeDao.upsert(entity)
                    entity
                } else cached
            }
            if (!canUseRemote(session)) return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a cargar la lista."))
            Result.success(pageFromFetchedWindow(localRows.map { it.toDomain() }, page, scope))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) readEmployeePage(actor, includeInactive, page, scope) else Result.failure(failure)
        }
    }

    override suspend fun getEmployeeById(id: String): Result<Employee> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("El identificador del empleado es obligatorio."))
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar empleados."))
        val actor = session.userId
        val local = employeeDao.getByLocalId(id)?.takeIf { it.cacheActorUserId == actor }
            ?: employeeDao.getByRemoteId(id, actor)
        if (local?.syncState == EmployeeEntity.CONFLICT) {
            return Result.success(local.toDomain())
        }
        if (!canUseRemote(session)) {
            return local?.let { Result.success(it.toDomain()) }
                ?: Result.failure(NoSuchElementException("El empleado no está disponible en el caché offline."))
        }
        return try {
            syncCoordinator.syncPending()
            val remoteId = local?.remoteId ?: id
            val rows = supabase.from(EMPLOYEES_TABLE)
                .select(columns = EMPLOYEE_COLUMNS) { filter { eq("id", remoteId) } }
                .decodeList<EmployeeDto>()
            val remote = rows.singleOrNull() ?: throw NoSuchElementException("No se encontró el empleado.")
            if (!canUseRemote(session)) return Result.failure(SecurityException("La sesión cambió durante la consulta; vuelve a sincronizar."))
            val updatedLocal = remote.toEntity(localId = local?.localId ?: scopedEmployeeId(actor, remote.id), cacheActorUserId = actor)
            employeeDao.upsert(updatedLocal)
            Result.success(updatedLocal.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure() && local != null) Result.success(local.toDomain())
            else Result.failure(failure)
        }
    }

    override suspend fun createEmployee(
        nombre: String,
        apellido: String,
        telefono: String?,
        salarioCents: Long,
        diaPago: Int,
    ): Result<Employee> {
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para crear empleados."))
        val actor = session.userId
        if (!canUseRemote(session)) {
            return Result.failure(IllegalStateException("Crear empleados requiere conexión para evitar registros duplicados."))
        }
        return try {
            syncCoordinator.syncPending()
            val saved = supabase.from(EMPLOYEES_TABLE).insert(
                UpsertEmployeeRequestDto(
                    nombre = nombre.trim(),
                    apellido = apellido.trim(),
                    telefono = telefono?.trim()?.ifBlank { null },
                    salario = salarioCents,
                    diaPago = diaPago,
                    activo = true,
                ),
            ) { select(columns = EMPLOYEE_COLUMNS) }
                .decodeSingle<EmployeeDto>()
            if (currentSession()?.userId != actor || supabase.auth.currentUserOrNull()?.id != actor) {
                return Result.failure(SecurityException("La sesión cambió mientras se creaba el empleado; vuelve a consultar antes de reintentar."))
            }
            val entity = saved.toEntity(localId = scopedEmployeeId(actor, saved.id), cacheActorUserId = actor)
            employeeDao.upsert(entity)
            Result.success(entity.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun updateEmployee(
        id: String,
        nombre: String,
        apellido: String,
        telefono: String?,
        salarioCents: Long,
        diaPago: Int,
        activo: Boolean,
    ): Result<Employee> = updateCachedEmployee(id) { employee ->
        employee.copy(
            nombre = nombre.trim(),
            apellido = apellido.trim(),
            telefono = telefono?.trim()?.ifBlank { null },
            salarioCents = salarioCents,
            diaPago = diaPago,
            activo = activo,
        )
    }

    override suspend fun toggleEmployeeActive(id: String, active: Boolean): Result<Unit> =
        updateCachedEmployee(id) { it.copy(activo = active) }.map { }

    private suspend fun updateCachedEmployee(
        id: String,
        update: (EmployeeEntity) -> EmployeeEntity,
    ): Result<Employee> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("El identificador del empleado es obligatorio."))
        val session = currentSession() ?: return Result.failure(SecurityException("Se requiere una sesión activa para actualizar empleados."))
        val actor = session.userId
        var current = employeeDao.getByLocalId(id)?.takeIf { it.cacheActorUserId == actor }
            ?: employeeDao.getByRemoteId(id, actor)
        if (current == null && canUseRemote(session)) {
            current = getEmployeeById(id).getOrElse { return Result.failure(it) }
                .let { employee -> employeeDao.getByLocalId(employee.id)?.takeIf { it.cacheActorUserId == actor } }
        }
        val existing = current ?: return Result.failure(
            NoSuchElementException("El empleado no está disponible en el caché local."),
        )
        if (existing.syncState == EmployeeEntity.CONFLICT) {
            return Result.failure(IllegalStateException("Este empleado tiene un conflicto de sincronización. Recarga y revisa el cambio antes de editarlo."))
        }
        val remoteId = existing.remoteId
            ?: return Result.failure(IllegalStateException("El perfil local aún no está vinculado con un empleado remoto."))
        val changed = update(existing).copy(
            syncState = EmployeeEntity.PENDING_UPDATE,
            updateActorUserId = actor,
            localUpdatedAtMillis = System.currentTimeMillis(),
            lastSyncError = null,
        )

        if (!canUseRemote(session)) {
            employeeDao.upsert(changed)
            return Result.success(changed.toDomain())
        }

        return try {
            syncCoordinator.syncPending()
            if (!canUseRemote(session)) {
                employeeDao.upsert(changed)
                return Result.success(changed.toDomain())
            }
            val rows = supabase.from(EMPLOYEES_TABLE).update(changed.toUpsertDto()) {
                filter {
                    eq("id", remoteId)
                    eq("sync_version", changed.syncVersion)
                }
                select(columns = EMPLOYEE_COLUMNS)
            }.decodeList<EmployeeDto>()
            if (currentSession()?.userId != actor || supabase.auth.currentUserOrNull()?.id != actor) {
                return Result.failure(SecurityException("La sesión cambió durante la actualización del empleado."))
            }
            val response = rows.singleOrNull()
            if (response == null) {
                val conflictSnapshot = try {
                    if (!canUseRemote(session)) null else supabase.from(EMPLOYEES_TABLE)
                        .select(columns = EMPLOYEE_COLUMNS) { filter { eq("id", remoteId) } }
                        .decodeList<EmployeeDto>()
                        .singleOrNull()
                        ?.toConflictSnapshotJson()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                employeeDao.upsert(
                    changed.copy(
                        syncState = EmployeeEntity.CONFLICT,
                        lastSyncError = "VERSION_CONFLICT",
                        conflictServerJson = conflictSnapshot,
                    ),
                )
                return Result.failure(IllegalStateException("El empleado cambió en otra sesión. Recarga sus datos y revisa el conflicto antes de volver a guardar."))
            }
            val saved = response.toEntity(localId = changed.localId, cacheActorUserId = actor)
            employeeDao.upsert(saved)
            Result.success(saved.toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                employeeDao.upsert(changed)
                Result.success(changed.toDomain())
            } else {
                Result.failure(failure)
            }
        }
    }

    private suspend fun cachedEmployees(actor: String, includeInactive: Boolean): Result<List<Employee>> = try {
        Result.success(employeeDao.getEmployees(actor, includeInactive).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun readEmployeePage(actor: String, includeInactive: Boolean, page: PageRequest, scope: String): Result<DataPage<Employee>> = try {
        val rows = employeeDao.getEmployeePage(actor, includeInactive, page.limit + 1, page.offset).map { it.toDomain() }
        Result.success(pageFromFetchedWindow(rows, page, scope, fetchedAt = null))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun currentSession(): AppUserSession? = (authRepository.state.value as? AuthState.SignedIn)?.session

    private suspend fun fetchAllEmployees(includeInactive: Boolean): List<EmployeeDto> {
        val rows = mutableListOf<EmployeeDto>()
        var offset = 0L
        while (true) {
            val page = supabase.from(EMPLOYEES_TABLE).select(columns = EMPLOYEE_COLUMNS) {
                if (!includeInactive) filter { eq("activo", true) }
                order("id", Order.ASCENDING)
                range(offset..(offset + SNAPSHOT_PAGE_SIZE - 1))
            }.decodeList<EmployeeDto>()
            rows += page
            if (page.size.toLong() < SNAPSHOT_PAGE_SIZE) return rows
            offset += SNAPSHOT_PAGE_SIZE
        }
    }

    private fun canUseRemote(session: AppUserSession): Boolean =
        !session.isOffline && networkMonitor.isOnline() && supabase.auth.currentUserOrNull()?.id == session.userId

    private fun scopedEmployeeId(actor: String, remoteId: String) = "$actor:$remoteId"

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
        const val EMPLOYEES_TABLE = "empleados"
        val EMPLOYEE_COLUMNS = Columns.list(
            "id, user_id, nombre, apellido, telefono, salario, dia_pago, activo, created_at, updated_at, sync_version",
        )
    }
}
