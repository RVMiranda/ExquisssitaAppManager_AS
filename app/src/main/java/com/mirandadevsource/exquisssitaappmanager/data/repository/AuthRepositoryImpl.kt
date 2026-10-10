package com.mirandadevsource.exquisssitaappmanager.data.repository

import com.mirandadevsource.exquisssitaappmanager.core.network.NetworkMonitor
import com.mirandadevsource.exquisssitaappmanager.core.network.SupabaseConfig
import com.mirandadevsource.exquisssitaappmanager.core.security.EncryptedSessionManager
import com.mirandadevsource.exquisssitaappmanager.core.security.SecureAuthStore
import com.mirandadevsource.exquisssitaappmanager.data.model.CachedAuthMetadata
import com.mirandadevsource.exquisssitaappmanager.data.model.EmployeeAuthProfileDto
import com.mirandadevsource.exquisssitaappmanager.data.model.UserRoleDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailure
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailureReason
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val config: SupabaseConfig,
    private val sessionManager: EncryptedSessionManager,
    private val secureAuthStore: SecureAuthStore,
    private val networkMonitor: NetworkMonitor,
) : AuthRepository {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Checking)
    private val operationMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val state: StateFlow<AuthState> = mutableState.asStateFlow()

    override suspend fun restoreSession() = operationMutex.withLock {
        mutableState.value = AuthState.Checking
        if (!config.isConfigured) {
            mutableState.value = AuthState.SignedOut
            return@withLock
        }

        val cached = readCachedMetadata()
        if (cached != null && !cached.isAllowedAppRole()) {
            clearLocalSession()
            mutableState.value = AuthState.SignedOut
            return@withLock
        }
        if (cached != null && !isWithinSessionLimit(cached.startedAtEpochMillis)) {
            clearLocalSession()
            mutableState.value = AuthState.SignedOut
            return@withLock
        }

        supabase.auth.awaitInitialization()
        val currentSession = supabase.auth.currentSessionOrNull()
        if (networkMonitor.isOnline() && currentSession == null) {
            clearLocalSession()
            mutableState.value = AuthState.SignedOut
            return@withLock
        }
        if (currentSession == null && cached == null) {
            mutableState.value = AuthState.SignedOut
            return@withLock
        }

        if (networkMonitor.isOnline() && currentSession != null) {
            try {
                val metadata = loadAndValidateProfile(currentSession.user?.id ?: throw notAuthorized())
                val startedAt = cached?.startedAtEpochMillis ?: System.currentTimeMillis()
                val refreshed = metadata.copy(startedAtEpochMillis = startedAt)
                saveMetadata(refreshed)
                mutableState.value = AuthState.SignedIn(refreshed.toAppSession(isOffline = false))
                return@withLock
            } catch (failure: AuthFailure) {
                if (failure.reason == AuthFailureReason.NOT_AUTHORIZED) {
                    clearLocalSession()
                    mutableState.value = AuthState.SignedOut
                    return@withLock
                }
                if (!failure.isNetworkFailure()) {
                    clearLocalSession()
                    mutableState.value = AuthState.SignedOut
                    return@withLock
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!failure.isNetworkFailure()) {
                    clearLocalSession()
                    mutableState.value = AuthState.SignedOut
                    return@withLock
                }
            }
        }

        if (cached != null) {
            mutableState.value = AuthState.SignedIn(cached.toAppSession(isOffline = true))
        } else {
            clearLocalSession()
            mutableState.value = AuthState.SignedOut
        }
    }

    override suspend fun signIn(email: String, password: String) = operationMutex.withLock {
        if (!config.isConfigured) throw AuthFailure(AuthFailureReason.CONFIGURATION)
        if (!networkMonitor.isOnline()) throw AuthFailure(AuthFailureReason.NETWORK)

        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw when {
                failure.isNetworkFailure() -> AuthFailure(AuthFailureReason.NETWORK, failure)
                failure.looksLikeCredentialFailure() -> AuthFailure(AuthFailureReason.INVALID_CREDENTIALS, failure)
                else -> AuthFailure(AuthFailureReason.UNKNOWN, failure)
            }
        }

        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: throw notAuthorized()
            val profile = loadAndValidateProfile(userId)
            val metadata = profile.copy(startedAtEpochMillis = System.currentTimeMillis())
            saveMetadata(metadata)
            mutableState.value = AuthState.SignedIn(metadata.toAppSession(isOffline = false))
        } catch (cancelled: CancellationException) {
            clearLocalSession()
            throw cancelled
        } catch (failure: Exception) {
            clearLocalSession()
            if (failure is AuthFailure) throw failure
            throw when {
                failure.isNetworkFailure() -> AuthFailure(AuthFailureReason.NETWORK, failure)
                else -> AuthFailure(AuthFailureReason.NOT_AUTHORIZED, failure)
            }
        }
    }

    override suspend fun signOut() = operationMutex.withLock {
        try {
            if (networkMonitor.isOnline() && supabase.auth.currentSessionOrNull() != null) {
                runCatching { supabase.auth.signOut() }
            }
        } finally {
            clearLocalSession()
            mutableState.value = AuthState.SignedOut
        }
    }

    override suspend fun expireSession() = signOut()

    private suspend fun loadAndValidateProfile(userId: String): CachedAuthMetadata {
        val roleRows = supabase.from("user_roles")
            .select(columns = Columns.list("role")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<UserRoleDto>()
        val roleRecord = roleRows.singleOrNull() ?: throw notAuthorized()
        val role = UserRole.fromDatabase(roleRecord.role) ?: throw notAuthorized()
        if (role !in setOf(UserRole.ADMIN, UserRole.GERENTE)) throw notAuthorized()

        val profileRows = supabase.from("empleados")
            .select(columns = Columns.list("id", "user_id", "nombre", "apellido", "activo")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<EmployeeAuthProfileDto>()
        val employee = profileRows.singleOrNull()

        if (employee != null && (!employee.activo || employee.userId != userId)) {
            throw notAuthorized()
        }

        if (employee == null && role != UserRole.ADMIN) {
            throw notAuthorized()
        }

        val employeeId = employee?.id.orEmpty()
        val employeeName = employee?.let {
            listOf(it.nombre, it.apellido).filter(String::isNotBlank).joinToString(" ")
        }?.ifBlank { null } ?: if (role == UserRole.ADMIN) "Administrador" else "Personal"

        return CachedAuthMetadata(
            userId = userId,
            email = supabase.auth.currentUserOrNull()?.email.orEmpty(),
            role = role.databaseValue,
            employeeId = employeeId,
            employeeName = employeeName,
            startedAtEpochMillis = 0L,
        )
    }

    private fun readCachedMetadata(): CachedAuthMetadata? = runCatching {
        secureAuthStore.get(METADATA_KEY)?.let {
            json.decodeFromString(CachedAuthMetadata.serializer(), it)
        }
    }.getOrNull()

    private fun saveMetadata(metadata: CachedAuthMetadata) {
        secureAuthStore.put(METADATA_KEY, json.encodeToString(CachedAuthMetadata.serializer(), metadata))
    }

    private suspend fun clearLocalSession() {
        secureAuthStore.remove(METADATA_KEY)
        supabase.auth.clearSession()
        sessionManager.deleteSession()
    }

    private fun isWithinSessionLimit(startedAt: Long): Boolean {
        val elapsed = System.currentTimeMillis() - startedAt
        return elapsed in 0..MAX_SESSION_MILLIS
    }

    private fun CachedAuthMetadata.isAllowedAppRole(): Boolean =
        UserRole.fromDatabase(role) == UserRole.ADMIN ||
            UserRole.fromDatabase(role) == UserRole.GERENTE

    private fun CachedAuthMetadata.toAppSession(isOffline: Boolean) = AppUserSession(
        userId = userId,
        email = email,
        role = UserRole.fromDatabase(role) ?: UserRole.EMPLEADO,
        employeeId = employeeId,
        employeeName = employeeName,
        startedAtEpochMillis = startedAtEpochMillis,
        isOffline = isOffline,
    )

    private fun AuthFailure.isNetworkFailure(): Boolean =
        reason == AuthFailureReason.NETWORK || cause?.isNetworkFailure() == true

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

    private fun Throwable.looksLikeCredentialFailure(): Boolean {
        val authErrorCode = generateSequence(this) { it.cause }
            .filterIsInstance<AuthRestException>()
            .firstOrNull()
            ?.errorCode
        if (authErrorCode == AuthErrorCode.InvalidCredentials) return true

        val message = generateSequence(this) { it.cause }.joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return message.contains("invalid login") || message.contains("invalid_credentials") ||
            message.contains("invalid credentials") || message.contains("email or password")
    }

    private fun notAuthorized() = AuthFailure(AuthFailureReason.NOT_AUTHORIZED)

    private companion object {
        const val METADATA_KEY = "auth_metadata"
        const val MAX_SESSION_MILLIS = 10L * 60L * 60L * 1000L
    }
}
