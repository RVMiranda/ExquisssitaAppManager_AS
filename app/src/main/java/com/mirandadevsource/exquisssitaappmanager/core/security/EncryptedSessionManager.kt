package com.mirandadevsource.exquisssitaappmanager.core.security

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedSessionManager @Inject constructor(
    private val secureAuthStore: SecureAuthStore,
) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveSession(session: UserSession) {
        secureAuthStore.put(SESSION_KEY, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val serialized = secureAuthStore.get(SESSION_KEY) ?: throw NoSessionFoundException()
        return json.decodeFromString(UserSession.serializer(), serialized)
    }

    override suspend fun deleteSession() {
        secureAuthStore.remove(SESSION_KEY)
    }

    private companion object {
        const val SESSION_KEY = "supabase_session"
    }
}
