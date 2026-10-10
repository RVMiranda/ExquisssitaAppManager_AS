package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toUpsertDto
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpsertEmployeeRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.sync.EmployeesPayrollSyncCoordinator
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

class EmployeeRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val employeeDao: EmployeeDao,
    private val syncCoordinator: EmployeesPayrollSyncCoordinator,
) : EmployeeRepository {

    override suspend fun getEmployees(includeInactive: Boolean): Result<List<Employee>> {
        if (!networkMonitor.isOnline()) return cachedEmployees(includeInactive)
        return try {
            syncCoordinator.syncPending()
            val remoteRows = supabase.from(EMPLOYEES_TABLE)
                .select(columns = EMPLOYEE_COLUMNS) {
                    if (!includeInactive) filter { eq("activo", true) }
                }
                .decodeList<EmployeeDto>()
            remoteRows.forEach { remote ->
                val cached = employeeDao.getByRemoteId(remote.id)
                if (cached == null || cached.syncState == EmployeeEntity.SYNCED) {
                    employeeDao.upsert(remote.toEntity(localId = cached?.localId ?: remote.id))
                }
            }
            cachedEmployees(includeInactive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) cachedEmployees(includeInactive)
            else Result.failure(failure)
        }
    }

    override suspend fun getEmployeeById(id: String): Result<Employee> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("El identificador del empleado es obligatorio."))
        val local = employeeDao.getByLocalId(id) ?: employeeDao.getByRemoteId(id)
        if (!networkMonitor.isOnline()) {
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
            val updatedLocal = remote.toEntity(localId = local?.localId ?: remote.id)
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
        if (!networkMonitor.isOnline()) {
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
            val entity = saved.toEntity(localId = saved.id)
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
        var current = employeeDao.getByLocalId(id) ?: employeeDao.getByRemoteId(id)
        if (current == null && networkMonitor.isOnline()) {
            current = getEmployeeById(id).getOrElse { return Result.failure(it) }
                .let { employee -> employeeDao.getByLocalId(employee.id) }
        }
        val existing = current ?: return Result.failure(
            NoSuchElementException("El empleado no está disponible en el caché local."),
        )
        val remoteId = existing.remoteId
            ?: return Result.failure(IllegalStateException("El perfil local aún no está vinculado con un empleado remoto."))
        val changed = update(existing).copy(
            syncState = EmployeeEntity.PENDING_UPDATE,
            localUpdatedAtMillis = System.currentTimeMillis(),
            lastSyncError = null,
        )

        if (!networkMonitor.isOnline()) {
            employeeDao.upsert(changed)
            return Result.success(changed.toDomain())
        }

        return try {
            syncCoordinator.syncPending()
            val rows = supabase.from(EMPLOYEES_TABLE).update(changed.toUpsertDto()) {
                filter { eq("id", remoteId) }
                select(columns = EMPLOYEE_COLUMNS)
            }.decodeList<EmployeeDto>()
            val response = rows.singleOrNull() ?: throw NoSuchElementException("No se encontró el empleado para actualizar.")
            val saved = response.toEntity(localId = changed.localId)
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

    private suspend fun cachedEmployees(includeInactive: Boolean): Result<List<Employee>> = try {
        Result.success(employeeDao.getEmployees(includeInactive).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
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
        const val EMPLOYEES_TABLE = "empleados"
        val EMPLOYEE_COLUMNS = Columns.list(
            "id, user_id, nombre, apellido, telefono, salario, dia_pago, activo, created_at, updated_at, sync_version",
        )
    }
}
