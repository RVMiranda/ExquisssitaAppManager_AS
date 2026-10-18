package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.CustomerCacheDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.CustomerCacheEntity
import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.model.CustomerDto
import com.mirandadevsource.exquisssitaappmanager.data.model.CustomerVisitDto
import com.mirandadevsource.exquisssitaappmanager.data.model.LoyaltyRewardDto
import com.mirandadevsource.exquisssitaappmanager.data.model.ManageLoyaltyRewardRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RedeemLoyaltyRewardRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RewardRedemptionDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterVisitRequestDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoyaltyReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardRedemption
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class LoyaltyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
    private val customerCacheDao: CustomerCacheDao,
) : LoyaltyRepository {

    override suspend fun getCustomerByQr(qrPayload: String): Result<Customer?> {
        val actor = currentActor()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar lealtad."))
        return remoteRequest {
        validateQrPayload(qrPayload)
        if (currentActor() != actor) throw SecurityException("La sesión cambió durante la consulta.")
        val rows = supabase.from(CUSTOMERS_TABLE)
            .select(columns = CUSTOMER_COLUMNS) {
                filter { eq("qr_token_hash", sha256(qrPayload)) }
            }
            .decodeList<CustomerDto>()
        rows.singleOrNull()?.let { dto ->
            customerCacheDao.upsert(dto.toCache(actor))
            dto.toDomain()
        }
        }
    }

    override suspend fun getCustomerByPhone(phone: String): Result<Customer?> {
        val normalizedPhone = phone.trim()
        if (normalizedPhone.isEmpty()) return Result.failure(IllegalArgumentException("El teléfono es obligatorio para buscar al cliente."))
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar lealtad."))
        val actor = session.userId
        if (session.isOffline || !networkMonitor.isOnline()) {
            return try {
                Result.success(customerCacheDao.findByPhone(normalizedPhone, actor)?.toDomain())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
        if (supabase.auth.currentUserOrNull()?.id != actor) {
            return Result.failure(SecurityException("La sesión remota no coincide con el usuario activo."))
        }

        val remote = remoteRequest {
            val rows = supabase.from(CUSTOMERS_TABLE)
                .select(columns = CUSTOMER_COLUMNS) { filter { eq("telefono", normalizedPhone) } }
                .decodeList<CustomerDto>()
            rows.singleOrNull()?.let { dto ->
                customerCacheDao.upsert(dto.toCache(actor))
                dto.toDomain()
            }
        }
        if (remote.isFailure && remote.exceptionOrNull()?.isNetworkFailure() == true) {
            return try {
                Result.success(customerCacheDao.findByPhone(normalizedPhone, actor)?.toDomain())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
        return remote
    }

    override suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit> {
        val actor = currentActor()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para registrar la visita."))
        return remoteRequest {
        validateQrPayload(qrPayload)
        if (currentActor() != actor) throw SecurityException("La sesión cambió durante la visita.")
        val response = supabase.postgrest
            .rpc("registrar_visita_qr", RegisterVisitRequestDto(qrPayload))
            .decodeAs<CustomerVisitDto>()

        val totals = if (response.visitasTotales == null || response.puntosLealtad == null) {
            supabase.from(CUSTOMERS_TABLE)
                .select(columns = CUSTOMER_COLUMNS) {
                    filter { eq("qr_token_hash", sha256(qrPayload)) }
                }
                .decodeList<CustomerDto>()
                .singleOrNull()
        } else null

        val cached = customerCacheDao.findById(response.clienteId, actor)
        val updatedCache = when {
            cached != null -> cached.copy(
                visitasTotales = response.visitasTotales ?: totals?.visitasTotales ?: cached.visitasTotales,
                puntosLealtad = response.puntosLealtad ?: totals?.puntosLealtad ?: cached.puntosLealtad,
                fetchedAtMillis = System.currentTimeMillis(),
            )
            totals != null -> totals.toCache(actor)
            else -> null
        }
        if (updatedCache != null) {
            customerCacheDao.upsert(updatedCache)
        }

        CustomerVisit(
            customerId = response.clienteId,
            date = response.fecha,
            pointsAwarded = response.puntosOtorgados ?: 0,
            totalVisits = response.visitasTotales ?: totals?.visitasTotales ?: cached?.visitasTotales ?: 0,
            totalLoyaltyPoints = response.puntosLealtad ?: totals?.puntosLealtad ?: cached?.puntosLealtad ?: 0,
            alreadyRegisteredToday = response.alreadyRegistered,
        )
        }
    }

    override suspend fun getRewards(includeInactive: Boolean): Result<List<LoyaltyReward>> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("El catálogo de recompensas requiere conexión."))
        }
        val signedIn = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para consultar recompensas."))
        if (includeInactive && signedIn.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Sólo administración puede consultar recompensas inactivas."))
        }
        return remoteRequest {
            supabase.from(REWARDS_TABLE).select(columns = REWARD_COLUMNS) {
                if (!includeInactive) filter { eq("activo", true) }
                order("titulo", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<LoyaltyRewardDto>().map { it.toDomain() }
        }
    }

    override suspend fun manageReward(
        rewardId: String,
        title: String,
        description: String,
        pointsCost: Int,
        active: Boolean,
        expectedVersion: Long,
    ): Result<LoyaltyReward> {
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()
        if (runCatching { UUID.fromString(rewardId) }.isFailure) {
            return Result.failure(IllegalArgumentException("El identificador de recompensa no es válido."))
        }
        if (normalizedTitle.isBlank() || normalizedTitle.length > 160) {
            return Result.failure(IllegalArgumentException("El nombre debe tener entre 1 y 160 caracteres."))
        }
        if (pointsCost <= 0 || expectedVersion < 0) {
            return Result.failure(IllegalArgumentException("Revisa la descripción, el costo en puntos y la versión."))
        }
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para administrar recompensas."))
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("Sólo administración puede gestionar recompensas."))
        }
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("La gestión de recompensas requiere conexión."))
        }
        return remoteRequest {
            supabase.postgrest.rpc(
                "manage_loyalty_reward",
                ManageLoyaltyRewardRequestDto(
                    id = rewardId,
                    title = normalizedTitle,
                    description = normalizedDescription,
                    pointsCost = pointsCost,
                    active = active,
                    expectedVersion = expectedVersion,
                ),
            ).decodeAs<LoyaltyRewardDto>().toDomain()
        }
    }

    override suspend fun redeemReward(
        qrPayload: String,
        rewardId: String,
        idempotencyKey: String,
    ): Result<RewardRedemption> {
        if (qrPayload.length != REDEEM_QR_TOKEN_LENGTH) {
            return Result.failure(IllegalArgumentException("El código QR no es válido para canjear."))
        }
        if (runCatching { UUID.fromString(rewardId) }.isFailure ||
            runCatching { UUID.fromString(idempotencyKey) }.isFailure
        ) {
            return Result.failure(IllegalArgumentException("La recompensa o la clave de operación no es válida."))
        }
        val actor = currentActor()
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para canjear recompensas."))
        return remoteRequest {
            val response = supabase.postgrest.rpc(
                "redeem_loyalty_reward",
                RedeemLoyaltyRewardRequestDto(qrPayload, rewardId, idempotencyKey),
            ).decodeAs<RewardRedemptionDto>()
            require(response.status == CONFIRMED_STATUS) {
                "Supabase no confirmó el canje. No se debe mostrar como realizado."
            }
            if (currentActor() != actor || supabase.auth.currentUserOrNull()?.id != actor) {
                throw SecurityException("La sesión cambió durante el canje; vuelve a consultar el resultado.")
            }
            val currentCustomer = customerCacheDao.findById(response.customerId, actor)
            val refreshedProfile = if (response.remainingPoints == null) {
                try {
                    fetchCustomer(response.customerId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The redemption receipt is authoritative even if a follow-up profile
                    // read fails; report a null balance rather than hiding the confirmed canje.
                    null
                }
            } else null
            val balance = response.remainingPoints ?: refreshedProfile?.puntosLealtad
            val updatedProfile = when {
                currentCustomer != null && balance != null -> currentCustomer.copy(
                    puntosLealtad = balance,
                    fetchedAtMillis = System.currentTimeMillis(),
                )
                currentCustomer == null && refreshedProfile != null -> refreshedProfile.toCache(actor)
                else -> null
            }
            if (updatedProfile != null) {
                customerCacheDao.upsert(updatedProfile)
            }
            response.toDomain(remainingPoints = balance)
        }
    }

    private suspend fun fetchCustomer(customerId: String): CustomerDto? =
        supabase.from(CUSTOMERS_TABLE).select(columns = CUSTOMER_COLUMNS) {
            filter { eq("id", customerId) }
        }.decodeList<CustomerDto>().singleOrNull()

    private suspend fun <T> remoteRequest(block: suspend () -> T): Result<T> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Se requiere conexión a internet para esta operación."))
        }
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session
            ?: return Result.failure(SecurityException("Se requiere una sesión activa para operar lealtad."))
        if (session.isOffline || supabase.auth.currentUserOrNull()?.id != session.userId) {
            return Result.failure(SecurityException("La sesión remota no está disponible para operar lealtad."))
        }
        return try {
            val result = block()
            val currentSession = (authRepository.state.value as? AuthState.SignedIn)?.session
            if (currentSession?.userId != session.userId || currentSession?.isOffline != false ||
                supabase.auth.currentUserOrNull()?.id != session.userId
            ) {
                Result.failure(SecurityException("La sesión cambió durante la operación; vuelve a intentarlo."))
            } else Result.success(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun CustomerDto.toDomain(): Customer = Customer(
        id = requireNotNull(id) { "Supabase devolvió un cliente sin identificador." },
        name = nombre,
        phone = telefono,
        totalVisits = visitasTotales ?: 0,
        loyaltyPoints = puntosLealtad ?: 0,
        registeredAt = fechaRegistro ?: createdAt,
        fetchedAt = Instant.now(),
        isCached = false,
    )

    private fun CustomerDto.toCache(actorUserId: String): CustomerCacheEntity = CustomerCacheEntity(
        id = requireNotNull(id) { "Supabase devolvió un cliente sin identificador." },
        cacheActorUserId = actorUserId,
        nombre = nombre,
        telefono = telefono,
        visitasTotales = visitasTotales ?: 0,
        puntosLealtad = puntosLealtad ?: 0,
        fechaRegistro = fechaRegistro ?: createdAt,
        fetchedAtMillis = System.currentTimeMillis(),
    )

    private fun CustomerCacheEntity.toDomain(): Customer = Customer(
        id = id,
        name = nombre,
        phone = telefono,
        totalVisits = visitasTotales,
        loyaltyPoints = puntosLealtad,
        registeredAt = fechaRegistro,
        fetchedAt = Instant.ofEpochMilli(fetchedAtMillis),
        isCached = true,
    )

    private fun LoyaltyRewardDto.toDomain(): LoyaltyReward = LoyaltyReward(
        id = id,
        title = titulo,
        description = descripcion,
        pointsCost = pointsCost,
        active = activo,
        syncVersion = syncVersion,
    )

    private fun RewardRedemptionDto.toDomain(remainingPoints: Int?): RewardRedemption = RewardRedemption(
        redemptionId = redemptionId,
        customerId = customerId,
        rewardId = rewardId,
        pointsCost = pointsCost,
        remainingPoints = remainingPoints,
        alreadyProcessed = alreadyProcessed,
    )

    private fun currentActor(): String? = (authRepository.state.value as? AuthState.SignedIn)?.session?.userId

    private fun canUseRemote(actorUserId: String): Boolean {
        val session = (authRepository.state.value as? AuthState.SignedIn)?.session ?: return false
        return session.userId == actorUserId && !session.isOffline && networkMonitor.isOnline() &&
            supabase.auth.currentUserOrNull()?.id == actorUserId
    }

    private fun validateQrPayload(payload: String) {
        require(payload.length in MIN_QR_TOKEN_LENGTH..MAX_QR_TOKEN_LENGTH) {
            "El código QR no es válido."
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException) return true
            val name = current::class.simpleName.orEmpty()
            if (name.contains("HttpRequestException") || name.contains("ConnectException") ||
                name.contains("UnknownHostException") || name.contains("SocketTimeoutException")
            ) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val CUSTOMERS_TABLE = "clientes"
        const val REWARDS_TABLE = "recompensas"
        const val MIN_QR_TOKEN_LENGTH = 32
        const val MAX_QR_TOKEN_LENGTH = 512
        const val REDEEM_QR_TOKEN_LENGTH = 64
        const val CONFIRMED_STATUS = "confirmed"
        val CUSTOMER_COLUMNS = Columns.list(
            "id, nombre, telefono, visitas_totales, puntos_lealtad, fecha_registro, created_at",
        )
        val REWARD_COLUMNS = Columns.list("id, titulo, descripcion, costo_puntos, activo, sync_version")
    }
}
