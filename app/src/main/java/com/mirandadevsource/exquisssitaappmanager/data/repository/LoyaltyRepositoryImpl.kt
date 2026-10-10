package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.data.model.CreateCustomerRequestDto
import com.mirandadevsource.exquisssitaappmanager.data.model.CustomerDto
import com.mirandadevsource.exquisssitaappmanager.data.model.CustomerVisitDto
import com.mirandadevsource.exquisssitaappmanager.data.model.RegisterVisitRequestDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CreatedCustomer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

class LoyaltyRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val networkMonitor: NetworkMonitor,
) : LoyaltyRepository {

    override suspend fun getCustomerByQr(qrPayload: String): Result<Customer?> = remoteRequest {
        validateQrPayload(qrPayload)
        val rows = supabase.from(CUSTOMERS_TABLE)
            .select(columns = CUSTOMER_COLUMNS) {
                filter { eq("qr_token_hash", sha256(qrPayload)) }
            }
            .decodeList<CustomerDto>()
        rows.singleOrNull()?.toDomain()
    }

    override suspend fun getCustomerByPhone(phone: String): Result<Customer?> = remoteRequest {
        val normalizedPhone = phone.trim()
        require(normalizedPhone.isNotEmpty()) { "El teléfono es obligatorio para buscar al cliente." }
        val rows = supabase.from(CUSTOMERS_TABLE)
            .select(columns = CUSTOMER_COLUMNS) {
                filter { eq("telefono", normalizedPhone) }
            }
            .decodeList<CustomerDto>()
        rows.singleOrNull()?.toDomain()
    }

    override suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit> = remoteRequest {
        validateQrPayload(qrPayload)
        val response = supabase.postgrest
            .rpc("registrar_visita_qr", RegisterVisitRequestDto(qrPayload))
            .decodeAs<CustomerVisitDto>()

        // The deployed RPC may omit totals for a duplicate response. Read the current
        // totals so callers always receive a useful domain result.
        val totals = if (response.visitasTotales == null || response.puntosLealtad == null) {
            supabase.from(CUSTOMERS_TABLE)
                .select(columns = CUSTOMER_COLUMNS) {
                    filter { eq("qr_token_hash", sha256(qrPayload)) }
                }
                .decodeList<CustomerDto>()
                .singleOrNull()
        } else {
            null
        }

        CustomerVisit(
            customerId = response.clienteId,
            date = response.fecha,
            pointsAwarded = response.puntosOtorgados ?: 0,
            totalVisits = response.visitasTotales ?: totals?.visitasTotales ?: 0,
            totalLoyaltyPoints = response.puntosLealtad ?: totals?.puntosLealtad ?: 0,
            alreadyRegisteredToday = response.alreadyRegistered,
        )
    }

    override suspend fun createCustomer(name: String, phone: String?): Result<CreatedCustomer> = remoteRequest {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "El nombre del cliente es obligatorio." }
        val normalizedPhone = phone?.trim()?.ifBlank { null }
        val qrPayload = newQrToken()
        val saved = supabase.from(CUSTOMERS_TABLE)
            .insert(
                CreateCustomerRequestDto(
                    nombre = normalizedName,
                    telefono = normalizedPhone,
                    qrTokenHash = sha256(qrPayload),
                ),
            ) {
                select(columns = CUSTOMER_COLUMNS)
            }
            .decodeSingle<CustomerDto>()

        CreatedCustomer(
            customer = saved.toDomain(),
            qrPayload = qrPayload,
        )
    }

    private suspend fun <T> remoteRequest(block: suspend () -> T): Result<T> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(IllegalStateException("Se requiere conexión a internet para esta operación."))
        }
        return try {
            Result.success(block())
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
    )

    private fun validateQrPayload(payload: String) {
        require(payload.length in MIN_QR_TOKEN_LENGTH..MAX_QR_TOKEN_LENGTH) {
            "El código QR no es válido."
        }
    }

    private fun newQrToken(): String = ByteArray(QR_TOKEN_BYTES)
        .also(secureRandom::nextBytes)
        .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val CUSTOMERS_TABLE = "clientes"
        const val MIN_QR_TOKEN_LENGTH = 32
        const val MAX_QR_TOKEN_LENGTH = 512
        const val QR_TOKEN_BYTES = 32
        val CUSTOMER_COLUMNS = Columns.list(
            "id, nombre, telefono, visitas_totales, puntos_lealtad, fecha_registro, created_at",
        )
        val secureRandom = SecureRandom()
    }
}
