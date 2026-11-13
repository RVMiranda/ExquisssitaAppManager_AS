package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.DailyClosureDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ExpenseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.ExpenseTemplateDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseTemplateEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.data.model.CreateExpenseRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.CreateExpenseTemplateRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.DailyProfitRpcParams
import com.mirandadevsource.exquisssitaappmanager.data.model.DailyProfitSummaryDto
import com.mirandadevsource.exquisssitaappmanager.data.model.ExpenseDto
import com.mirandadevsource.exquisssitaappmanager.data.model.ExpenseTemplateDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpdateExpenseRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UpdateExpenseTemplateRequestDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyProfitSummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Expense
import com.mirandadevsource.exquisssitaappmanager.domain.entity.ExpenseTemplate
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.ExpenseRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val expenseDao: ExpenseDao,
    private val expenseTemplateDao: ExpenseTemplateDao,
    private val dailyClosureDao: DailyClosureDao,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
) : ExpenseRepository {

    override fun getExpensesFlow(date: String): Flow<List<Expense>> {
        return authRepository.state.flatMapLatest { state ->
            val actor = (state as? AuthState.SignedIn)?.session?.userId
            if (actor.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                expenseDao.getExpensesForDateFlow(actor, date).map { entities ->
                    entities
                        .filter { it.syncState != ExpenseEntity.PENDING_DELETE }
                        .map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun refreshExpenses(date: String): Result<List<Expense>> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar gastos."))
        val actor = session.userId

        if (!canUseRemote(session)) {
            return readCachedExpenses(actor, date)
        }

        return try {
            syncPendingExpenses()

            val remoteRows = supabase.from(EXPENSES_TABLE).select {
                filter { eq("fecha", date) }
                order("created_at", Order.DESCENDING)
            }.decodeList<ExpenseDto>()

            val remoteIds = remoteRows.map { it.id }
            if (remoteIds.isNotEmpty()) {
                expenseDao.deleteSyncedNotIn(actor, date, remoteIds)
            } else {
                expenseDao.deleteAllSyncedForDate(actor, date)
            }

            remoteRows.forEach { remote ->
                val cached = expenseDao.getByRemoteId(remote.id, actor)
                if (cached == null || cached.syncState == ExpenseEntity.SYNCED) {
                    expenseDao.upsert(remote.toEntity(actor))
                }
            }

            readCachedExpenses(actor, date)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                readCachedExpenses(actor, date)
            } else {
                Result.failure(failure)
            }
        }
    }

    override suspend fun saveExpense(expense: Expense): Result<Expense> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para guardar gastos."))
        val actor = session.userId

        val effectiveLocalId = expense.localId.ifBlank { UUID.randomUUID().toString() }
        val nowIso = Instant.now().toString()

        val isUpdate = !expense.remoteId.isNullOrBlank()

        if (canUseRemote(session)) {
            return try {
                val savedDto = if (isUpdate) {
                    val updatePayload = UpdateExpenseRequestDto(
                        fecha = expense.date,
                        concepto = expense.concept.trim(),
                        categoria = expense.category.trim(),
                        monto = expense.amountCents,
                        cantidad = expense.quantity,
                        precioUnitario = expense.unitPriceCents,
                        metodoPago = expense.paymentMethod.rawValue,
                        plantillaId = expense.templateId,
                        notas = expense.notes?.trim()?.ifBlank { null },
                        updatedAt = nowIso,
                    )
                    supabase.from(EXPENSES_TABLE).update(updatePayload) {
                        filter { eq("id", expense.remoteId) }
                        select()
                    }.decodeSingle<ExpenseDto>()
                } else {
                    val createPayload = CreateExpenseRequestDto(
                        id = effectiveLocalId,
                        fecha = expense.date,
                        concepto = expense.concept.trim(),
                        categoria = expense.category.trim(),
                        monto = expense.amountCents,
                        cantidad = expense.quantity,
                        precioUnitario = expense.unitPriceCents,
                        metodoPago = expense.paymentMethod.rawValue,
                        plantillaId = expense.templateId,
                        notas = expense.notes?.trim()?.ifBlank { null },
                        registradoPor = actor,
                    )
                    supabase.from(EXPENSES_TABLE).insert(createPayload) {
                        select()
                    }.decodeSingle<ExpenseDto>()
                }

                val entity = savedDto.toEntity(actor)
                expenseDao.upsert(entity)
                Result.success(entity.toDomain())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure.isNetworkFailure()) {
                    saveLocalPending(expense, effectiveLocalId, actor, isUpdate)
                } else {
                    Result.failure(failure)
                }
            }
        } else {
            return saveLocalPending(expense, effectiveLocalId, actor, isUpdate)
        }
    }

    private suspend fun saveLocalPending(
        expense: Expense,
        localId: String,
        actor: String,
        isUpdate: Boolean,
    ): Result<Expense> {
        val syncState = if (isUpdate) ExpenseEntity.PENDING_UPDATE else ExpenseEntity.PENDING_INSERT
        val entity = expense.copy(localId = localId).toEntity(actor, syncState = syncState)
        expenseDao.upsert(entity)
        return Result.success(entity.toDomain())
    }

    override suspend fun deleteExpense(localId: String): Result<Unit> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para eliminar gastos."))
        val actor = session.userId

        val cached = expenseDao.getByLocalId(localId) ?: return Result.success(Unit)

        if (cached.remoteId.isNullOrBlank()) {
            expenseDao.deleteByLocalId(localId)
            return Result.success(Unit)
        }

        if (canUseRemote(session)) {
            return try {
                supabase.from(EXPENSES_TABLE).delete {
                    filter { eq("id", cached.remoteId) }
                }
                expenseDao.deleteByLocalId(localId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure.isNetworkFailure()) {
                    expenseDao.upsert(cached.copy(syncState = ExpenseEntity.PENDING_DELETE))
                    Result.success(Unit)
                } else {
                    Result.failure(failure)
                }
            }
        } else {
            expenseDao.upsert(cached.copy(syncState = ExpenseEntity.PENDING_DELETE))
            return Result.success(Unit)
        }
    }

    override suspend fun syncPendingExpenses(): Result<Int> {
        val session = currentSession() ?: return Result.success(0)
        if (!canUseRemote(session)) return Result.success(0)

        val actor = session.userId
        val pendingList = expenseDao.getPendingSync(actor)
        if (pendingList.isEmpty()) return Result.success(0)

        var syncedCount = 0
        for (item in pendingList) {
            try {
                when (item.syncState) {
                    ExpenseEntity.PENDING_DELETE -> {
                        if (!item.remoteId.isNullOrBlank()) {
                            supabase.from(EXPENSES_TABLE).delete {
                                filter { eq("id", item.remoteId) }
                            }
                        }
                        expenseDao.deleteByLocalId(item.localId)
                        syncedCount++
                    }
                    ExpenseEntity.PENDING_INSERT -> {
                        val payload = CreateExpenseRequestDto(
                            id = item.localId,
                            fecha = item.fecha,
                            concepto = item.concepto,
                            categoria = item.categoria,
                            monto = item.montoCents,
                            cantidad = item.cantidad,
                            precioUnitario = item.precioUnitarioCents,
                            metodoPago = item.metodoPago,
                            plantillaId = item.plantillaId,
                            notas = item.notas,
                            registradoPor = item.registradoPor ?: actor,
                        )
                        val remote = supabase.from(EXPENSES_TABLE).insert(payload) {
                            select()
                        }.decodeSingle<ExpenseDto>()
                        expenseDao.upsert(remote.toEntity(actor))
                        syncedCount++
                    }
                    ExpenseEntity.PENDING_UPDATE -> {
                        val remoteId = item.remoteId ?: item.localId
                        val payload = UpdateExpenseRequestDto(
                            fecha = item.fecha,
                            concepto = item.concepto,
                            categoria = item.categoria,
                            monto = item.montoCents,
                            cantidad = item.cantidad,
                            precioUnitario = item.precioUnitarioCents,
                            metodoPago = item.metodoPago,
                            plantillaId = item.plantillaId,
                            notas = item.notas,
                            updatedAt = Instant.now().toString(),
                        )
                        val remote = supabase.from(EXPENSES_TABLE).update(payload) {
                            filter { eq("id", remoteId) }
                            select()
                        }.decodeSingle<ExpenseDto>()
                        expenseDao.upsert(remote.toEntity(actor))
                        syncedCount++
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Exception) {
                if (err.isNetworkFailure()) {
                    break
                } else {
                    expenseDao.upsert(item.copy(syncState = ExpenseEntity.SYNC_ERROR, syncError = err.message))
                }
            }
        }
        return Result.success(syncedCount)
    }

    override fun getTemplatesFlow(): Flow<List<ExpenseTemplate>> {
        return authRepository.state.flatMapLatest { state ->
            val actor = (state as? AuthState.SignedIn)?.session?.userId
            if (actor.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                expenseTemplateDao.getActiveTemplatesFlow(actor).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override fun getAllTemplatesFlow(): Flow<List<ExpenseTemplate>> {
        return authRepository.state.flatMapLatest { state ->
            val actor = (state as? AuthState.SignedIn)?.session?.userId
            if (actor.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                expenseTemplateDao.getAllTemplatesFlow(actor).map { list ->
                    list.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun refreshTemplates(): Result<List<ExpenseTemplate>> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar plantillas."))
        val actor = session.userId

        if (!canUseRemote(session)) {
            return readCachedTemplates(actor, includeInactive = true)
        }

        return try {
            val remoteRows = supabase.from(EXPENSE_TEMPLATES_TABLE).select {
                order("nombre", Order.ASCENDING)
            }.decodeList<ExpenseTemplateDto>()

            val remoteIds = remoteRows.map { it.id }
            if (remoteIds.isNotEmpty()) {
                expenseTemplateDao.deleteSyncedNotIn(actor, remoteIds)
            }

            remoteRows.forEach { remote ->
                expenseTemplateDao.upsert(remote.toEntity(actor))
            }

            readCachedTemplates(actor, includeInactive = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                readCachedTemplates(actor, includeInactive = true)
            } else {
                Result.failure(failure)
            }
        }
    }

    override suspend fun saveTemplate(template: ExpenseTemplate): Result<ExpenseTemplate> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para gestionar plantillas."))
        val actor = session.userId

        val templateId = if (template.id.isBlank()) UUID.randomUUID().toString() else template.id
        val entity = template.copy(localId = templateId).toEntity(actor)

        if (canUseRemote(session)) {
            return try {
                val existing = if (template.id.isNotBlank()) expenseTemplateDao.getByRemoteId(template.id, actor) else null
                val savedDto = if (existing == null && template.remoteId.isNullOrBlank()) {
                    val payload = CreateExpenseTemplateRequestDto(
                        id = templateId,
                        nombre = template.name,
                        categoria = template.category,
                        precioUnitario = template.unitPriceCents,
                        unidadMedida = template.unitOfMeasure,
                        activo = template.isActive,
                    )
                    supabase.from(EXPENSE_TEMPLATES_TABLE).insert(payload) {
                        select()
                    }.decodeSingle<ExpenseTemplateDto>()
                } else {
                    val remoteId = template.remoteId ?: template.id
                    val payload = UpdateExpenseTemplateRequestDto(
                        nombre = template.name,
                        categoria = template.category,
                        precioUnitario = template.unitPriceCents,
                        unidadMedida = template.unitOfMeasure,
                        activo = template.isActive,
                        updatedAt = Instant.now().toString(),
                    )
                    supabase.from(EXPENSE_TEMPLATES_TABLE).update(payload) {
                        filter { eq("id", remoteId) }
                        select()
                    }.decodeSingle<ExpenseTemplateDto>()
                }
                val savedEntity = savedDto.toEntity(actor)
                expenseTemplateDao.upsert(savedEntity)
                Result.success(savedEntity.toDomain())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure.isNetworkFailure()) {
                    expenseTemplateDao.upsert(entity)
                    Result.success(entity.toDomain())
                } else {
                    Result.failure(failure)
                }
            }
        } else {
            expenseTemplateDao.upsert(entity)
            return Result.success(entity.toDomain())
        }
    }

    override suspend fun deleteTemplate(templateId: String): Result<Unit> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para eliminar plantillas."))

        if (canUseRemote(session)) {
            try {
                supabase.from(EXPENSE_TEMPLATES_TABLE).delete {
                    filter { eq("id", templateId) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!failure.isNetworkFailure()) {
                    return Result.failure(failure)
                }
            }
        }
        expenseTemplateDao.deleteByLocalId(templateId)
        return Result.success(Unit)
    }

    override suspend fun toggleTemplateActive(templateId: String, active: Boolean): Result<Unit> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para modificar plantillas."))
        val actor = session.userId

        val cached = expenseTemplateDao.getByRemoteId(templateId, actor)
            ?: expenseTemplateDao.getByLocalId(templateId)
            ?: return Result.failure(IllegalArgumentException("Plantilla no encontrada"))

        val updated = cached.copy(activo = active, localUpdatedAtMillis = System.currentTimeMillis())
        expenseTemplateDao.upsert(updated)

        if (canUseRemote(session)) {
            try {
                val remoteId = cached.remoteId ?: templateId
                val payload = UpdateExpenseTemplateRequestDto(
                    nombre = cached.nombre,
                    categoria = cached.categoria,
                    precioUnitario = cached.precioUnitarioCents,
                    unidadMedida = cached.unidadMedida,
                    activo = active,
                    updatedAt = Instant.now().toString(),
                )
                supabase.from(EXPENSE_TEMPLATES_TABLE).update(payload) {
                    filter { eq("id", remoteId) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Silently tolerate network failures as Room is already updated
            }
        }
        return Result.success(Unit)
    }

    override suspend fun getDailyProfitSummary(date: String): Result<DailyProfitSummary> {
        val session = currentSession()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar rentabilidad."))
        val actor = session.userId

        if (canUseRemote(session)) {
            try {
                val remoteSummary = supabase.postgrest.rpc(
                    PROFIT_SUMMARY_RPC,
                    DailyProfitRpcParams(fecha = date),
                ).decodeAs<DailyProfitSummaryDto>()
                return Result.success(remoteSummary.toDomain())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!failure.isNetworkFailure()) {
                    return Result.failure(failure)
                }
            }
        }

        // Respaldo de cálculo local offline usando agregaciones en Room
        return try {
            val salesSummary = dailyClosureDao.getDaySummary(date)
            val totalExpensesCents = expenseDao.getTotalExpensesCentsForDate(actor, date)
            val cashExpensesCents = expenseDao.getExpensesCentsByPaymentMethod(actor, date, "Efectivo")
            val otherExpensesCents = totalExpensesCents - cashExpensesCents

            val totalSalesCents = salesSummary?.totalCents ?: 0L
            val cashSalesCents = salesSummary?.cashCents ?: 0L
            val cardSalesCents = salesSummary?.cardCents ?: 0L
            val otherSalesCents = salesSummary?.otherCents ?: 0L
            val closedOrdersCount = salesSummary?.closedOrderCount ?: 0

            val totalPayrollCents = 0L
            val totalOutflowsCents = totalExpensesCents + totalPayrollCents
            val netProfitCents = totalSalesCents - totalOutflowsCents
            val expectedCashInDrawerCents = cashSalesCents - cashExpensesCents - totalPayrollCents

            val offlineSummary = DailyProfitSummary(
                date = date,
                totalSalesCents = totalSalesCents,
                cashSalesCents = cashSalesCents,
                cardSalesCents = cardSalesCents,
                otherSalesCents = otherSalesCents,
                closedOrdersCount = closedOrdersCount,
                totalExpensesCents = totalExpensesCents,
                cashExpensesCents = cashExpensesCents,
                otherExpensesCents = otherExpensesCents,
                registeredExpensesCount = 0,
                totalPayrollCents = totalPayrollCents,
                payrollPaymentsCount = 0,
                totalOutflowsCents = totalOutflowsCents,
                netProfitCents = netProfitCents,
                expectedCashInDrawerCents = expectedCashInDrawerCents,
            )
            Result.success(offlineSummary)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun readCachedExpenses(actor: String, date: String): Result<List<Expense>> = try {
        val list = expenseDao.getExpensesForDate(actor, date)
            .filter { it.syncState != ExpenseEntity.PENDING_DELETE }
            .map { it.toDomain() }
        Result.success(list)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun readCachedTemplates(actor: String, includeInactive: Boolean = false): Result<List<ExpenseTemplate>> = try {
        val list = expenseTemplateDao.getTemplates(actor, includeInactive = includeInactive).map { it.toDomain() }
        Result.success(list)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun currentSession() = (authRepository.state.value as? AuthState.SignedIn)?.session

    private fun canUseRemote(session: com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession): Boolean {
        val roleAllowed = session.role == UserRole.ADMIN || session.role == UserRole.GERENTE
        return roleAllowed && !session.isOffline && networkMonitor.isOnline() && supabase.auth.currentUserOrNull()?.id == session.userId
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

    companion object {
        private const val EXPENSES_TABLE = "gastos"
        private const val EXPENSE_TEMPLATES_TABLE = "plantillas_gastos"
        private const val PROFIT_SUMMARY_RPC = "obtener_resumen_rentabilidad_diaria"
    }
}
