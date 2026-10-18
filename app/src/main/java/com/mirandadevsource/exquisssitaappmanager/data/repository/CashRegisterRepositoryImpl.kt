package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.DailyClosureDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.OrderCommandDao
import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderCommandEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.DailyClosureDto
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toDomain
import com.mirandadevsource.exquisssitaappmanager.data.mappers.toEntity
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.CashRegisterRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class CashRegisterRepositoryImpl @Inject constructor(
    supabase: SupabaseClient,
    networkMonitor: NetworkMonitor,
    commandDao: OrderCommandDao,
    leaseDao: SyncExecutionLeaseDao,
    authRepository: AuthRepository,
    private val closureDao: DailyClosureDao,
) : OrderSyncSupport(supabase, networkMonitor, commandDao, authRepository, leaseDao), CashRegisterRepository {

    override suspend fun closeCurrentPeriod(): Result<DailyClosure> {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(IllegalStateException("Inicia sesión para realizar el corte."))
        if (session.role != UserRole.ADMIN && session.role != UserRole.GERENTE) {
            return Result.failure(SecurityException("Solo admin o gerente activo puede realizar el corte."))
        }
        if (session.isOffline || !networkMonitor.isOnline() || !hasRemoteSession()) {
            return Result.failure(IllegalStateException("El corte requiere conexión y una sesión remota válida."))
        }

        val businessDate = LocalDate.now(BUSINESS_ZONE).toString()
        return try {
            // A previous request may have reached Postgres while its response was lost. Retry its
            // immutable payload/key first; never manufacture a second cut for an uncertain result.
            val latestCommand = commandDao.getLatestForEntity(session.userId, CUT_RPC, businessDate)
            if (latestCommand?.status == OrderCommandEntity.SYNCED) {
                val confirmed = latestCommand.resultJson?.let(::decodeClosureResult)
                    ?: throw IllegalStateException("El corte sincronizado no tiene una respuesta recuperable.")
                if (closureDao.getById(confirmed.id) == null) {
                    closureDao.upsertAll(listOf(confirmed.toEntity()))
                    return Result.success(confirmed.toDomain())
                }
            }
            val pending = commandDao.getPendingForEntity(session.userId, CUT_RPC, businessDate)
            val submitted = if (pending != null) {
                submit(pending)
            } else {
                val request = buildJsonObject { put("p_fecha", businessDate) }
                val command = OrderCommandEntity(
                    idempotencyKey = UUID.randomUUID().toString(),
                    actorUserId = session.userId,
                    aggregateId = businessDate,
                    entity = CUT_RPC,
                    entityId = businessDate,
                    operation = "INSERT",
                    payloadJson = Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), request),
                    expectedVersion = 0,
                )
                commandDao.insert(command)
                submit(command)
            }

            when (submitted.status) {
                OrderCommandEntity.SYNCED -> {
                    val dto = submitted.resultJson?.let(::decodeClosureResult)
                        ?: return Result.failure(IllegalStateException("La respuesta del corte no se pudo recuperar."))
                    closureDao.upsertAll(listOf(dto.toEntity()))
                    Result.success(dto.toDomain())
                }
                OrderCommandEntity.REJECTED -> Result.failure(IllegalStateException(cutFailureMessage(submitted.lastErrorCode)))
                else -> Result.failure(IllegalStateException("La solicitud quedó pendiente de confirmación. Vuelve a consultar antes de iniciar otro corte."))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    override suspend fun getCutsForDay(businessDate: String): Result<List<DailyClosure>> {
        if (!DATE_PATTERN.matches(businessDate)) return Result.failure(IllegalArgumentException("La fecha debe tener formato AAAA-MM-DD."))
        return try {
            if (networkMonitor.isOnline() && hasRemoteSession()) {
                val remote = supabase.from(CUTS_TABLE).select(columns = CUT_COLUMNS) {
                    filter { eq("fecha", businessDate) }
                    order("numero_corte", Order.ASCENDING)
                }.decodeList<DailyClosureDto>()
                closureDao.replaceDay(businessDate, remote.map { it.toEntity() })
            }
            Result.success(closureDao.getCutsForDay(businessDate).map { it.toDomain() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure.isNetworkFailure()) {
                readCachedDay(businessDate)
            } else {
                Result.failure(failure)
            }
        }
    }

    override suspend fun getDaySummary(businessDate: String): Result<CashRegisterDaySummary> =
        getCutsForDay(businessDate).map { cuts ->
            CashRegisterDaySummary(
                businessDate = businessDate,
                cutCount = cuts.size,
                cashCents = cuts.sumOf { it.cashCents },
                cardCents = cuts.sumOf { it.cardCents },
                otherCents = cuts.sumOf { it.otherCents },
                totalCents = cuts.sumOf { it.totalCents },
                closedOrderCount = cuts.sumOf { it.closedOrderCount },
            )
        }

    private suspend fun readCachedDay(date: String): Result<List<DailyClosure>> = try {
        Result.success(closureDao.getCutsForDay(date).map { it.toDomain() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun decodeClosureResult(resultJson: String): DailyClosureDto =
        Json.decodeFromString<com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncCommandResponse>(resultJson).toClosureDto()

    private fun com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.SyncCommandResponse.toClosureDto(): DailyClosureDto {
        val closureId = id ?: throw IllegalStateException("La confirmación no incluyó el identificador del corte.")
        val periodEnd = periodoFin ?: throw IllegalStateException("La confirmación no incluyó el fin del periodo.")
        return DailyClosureDto(
            id = closureId,
            businessDate = fecha ?: throw IllegalStateException("La confirmación no incluyó la fecha del corte."),
            cutNumber = numeroCorte ?: throw IllegalStateException("La confirmación no incluyó el consecutivo del corte."),
            periodStart = periodoInicio ?: throw IllegalStateException("La confirmación no incluyó el inicio del periodo."),
            periodEnd = periodEnd,
            cashCents = totalEfectivo ?: throw IllegalStateException("La confirmación no incluyó el total en efectivo."),
            cardCents = totalTarjeta ?: throw IllegalStateException("La confirmación no incluyó el total con tarjeta."),
            otherCents = totalOtro ?: throw IllegalStateException("La confirmación no incluyó el total de otros pagos."),
            totalCents = totalGlobal ?: throw IllegalStateException("La confirmación no incluyó el total global."),
            closedOrderCount = numOrdenes ?: throw IllegalStateException("La confirmación no incluyó el número de órdenes."),
            closedAt = horaCierre ?: periodEnd,
            closedBy = cerradoPor,
            syncVersion = syncVersion ?: 1L,
        )
    }

    private fun cutFailureMessage(code: String?): String = when {
        code?.contains("open_orders", ignoreCase = true) == true -> "No se puede realizar el corte mientras haya comandas abiertas."
        code?.contains("cut_limit", ignoreCase = true) == true -> "Ya se alcanzó el máximo de cinco cortes del día."
        code?.contains("not_authorized", ignoreCase = true) == true -> "Tu cuenta no está autorizada para realizar el corte."
        else -> code ?: "Supabase rechazó el corte de caja."
    }

    private companion object {
        const val CUTS_TABLE = "cortes_caja"
        const val CUT_RPC = "realizar_corte_caja_seguro"
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Mexico_City")
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val CUT_COLUMNS = Columns.list(
            "id, fecha, numero_corte, periodo_inicio, periodo_fin, total_efectivo, total_tarjeta, total_otro, total_global, num_ordenes, hora_cierre, cerrado_por, sync_version",
        )
    }
}
