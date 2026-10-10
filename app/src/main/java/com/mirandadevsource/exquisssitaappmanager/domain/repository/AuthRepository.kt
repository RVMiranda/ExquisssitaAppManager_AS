package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import kotlinx.coroutines.flow.StateFlow

enum class AuthFailureReason {
    CONFIGURATION,
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    NETWORK,
    NOT_AUTHORIZED,
    UNKNOWN,
}

class AuthFailure(
    val reason: AuthFailureReason,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

interface AuthRepository {
    val state: StateFlow<AuthState>

    suspend fun restoreSession()
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun expireSession()
}
