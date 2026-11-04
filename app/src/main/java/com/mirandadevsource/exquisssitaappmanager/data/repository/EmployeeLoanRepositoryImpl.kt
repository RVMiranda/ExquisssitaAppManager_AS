package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.EmployeeDao
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.model.CreateLoanRpcParams
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeLoanDto
import com.mirandadevsource.exquisssitaappmanager.data.model.LoanPaymentDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterLoanPaymentRpcParams
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanPayment
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.EmployeeLoanRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class EmployeeLoanRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val employeeDao: EmployeeDao,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
) : EmployeeLoanRepository {

    override suspend fun getAllLoans(): Result<List<EmployeeLoan>> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar préstamos."))
        if (!networkMonitor.isOnline() || session.isOffline) {
            return Result.failure(IllegalStateException("La consulta de préstamos requiere conexión a internet."))
        }
        return try {
            val rows = supabase.from(LOANS_TABLE).select {
                order("created_at", Order.DESCENDING)
            }.decodeList<EmployeeLoanDto>()

            val actor = session.userId
            val localEmployees = employeeDao.getEmployees(actor, includeInactive = true).associateBy { it.remoteId ?: it.localId }
            val domainList = rows.map { dto ->
                val emp = localEmployees[dto.empleadoId]
                val name = emp?.let { "${it.nombre} ${it.apellido}".trim() }
                dto.toDomain(name)
            }
            Result.success(domainList)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun getLoansForEmployee(empleadoId: String): Result<List<EmployeeLoan>> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar préstamos."))
        return try {
            val actor = session.userId
            val emp = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
                ?: employeeDao.getByRemoteId(empleadoId, actor)
                ?: employeeDao.getByLocalId(empleadoId)
            val remoteEmployeeId = emp?.remoteId
                ?: (if (empleadoId.contains(':')) empleadoId.substringAfter(':') else empleadoId)

            val rows = supabase.from(LOANS_TABLE).select {
                filter {
                    eq("empleado_id", remoteEmployeeId)
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<EmployeeLoanDto>()

            val name = emp?.let { "${it.nombre} ${it.apellido}".trim() }
            Result.success(rows.map { it.toDomain(name) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun getLoanPayments(prestamoId: String): Result<List<LoanPayment>> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa."))
        return try {
            val rows = supabase.from(PAYMENTS_TABLE).select {
                filter {
                    eq("prestamo_id", prestamoId)
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<LoanPaymentDto>()

            Result.success(rows.map { it.toDomain() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun createLoan(
        empleadoId: String,
        montoCentavos: Long,
        fecha: String?,
        notas: String?,
    ): Result<EmployeeLoan> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para otorgar préstamos."))
        return try {
            val actor = session.userId
            val emp = employeeDao.getByLocalId(empleadoId)?.takeIf { it.cacheActorUserId == actor }
                ?: employeeDao.getByRemoteId(empleadoId, actor)
                ?: employeeDao.getByLocalId(empleadoId)
            val remoteEmployeeId = emp?.remoteId
                ?: (if (empleadoId.contains(':')) empleadoId.substringAfter(':') else empleadoId)

            val created = supabase.postgrest.rpc(
                CREATE_LOAN_RPC,
                CreateLoanRpcParams(
                    empleadoId = remoteEmployeeId,
                    montoCentavos = montoCentavos,
                    fecha = fecha,
                    notas = notas?.trim()?.ifBlank { null },
                ),
            ).decodeAs<EmployeeLoanDto>()

            val name = emp?.let { "${it.nombre} ${it.apellido}".trim() }
                ?: employeeDao.getByRemoteId(created.empleadoId, actor)?.let { "${it.nombre} ${it.apellido}".trim() }

            Result.success(created.toDomain(name))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun registerPayment(
        prestamoId: String,
        montoCentavos: Long,
        tipo: String,
        notas: String?,
    ): Result<EmployeeLoan> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para abonar al préstamo."))
        return try {
            val updated = supabase.postgrest.rpc(
                REGISTER_PAYMENT_RPC,
                RegisterLoanPaymentRpcParams(
                    prestamoId = prestamoId,
                    montoCentavos = montoCentavos,
                    tipo = tipo,
                    notas = notas?.trim()?.ifBlank { null },
                ),
            ).decodeAs<EmployeeLoanDto>()

            val actor = session.userId
            val emp = employeeDao.getByRemoteId(updated.empleadoId, actor)
                ?: employeeDao.getByLocalId(updated.empleadoId)
            val name = emp?.let { "${it.nombre} ${it.apellido}".trim() }

            Result.success(updated.toDomain(name))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private companion object {
        const val LOANS_TABLE = "prestamos_empleados"
        const val PAYMENTS_TABLE = "abonos_prestamos_empleados"
        const val CREATE_LOAN_RPC = "crear_prestamo_empleado"
        const val REGISTER_PAYMENT_RPC = "registrar_abono_prestamo"
    }
}
