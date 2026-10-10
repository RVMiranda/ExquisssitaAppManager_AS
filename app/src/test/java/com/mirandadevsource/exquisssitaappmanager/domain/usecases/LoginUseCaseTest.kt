package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailure
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailureReason
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginUseCaseTest {
    @Test
    fun `trims email and delegates credentials`() = runBlocking {
        val repository = RecordingAuthRepository()

        LoginUseCase(repository)("  worker@example.com ", "correct horse")

        assertEquals("worker@example.com", repository.email)
        assertEquals("correct horse", repository.password)
    }

    @Test
    fun `rejects malformed email before network call`() {
        val repository = RecordingAuthRepository()

        val failure = assertThrows(AuthFailure::class.java) {
            runBlocking { LoginUseCase(repository)("not-an-email", "password") }
        }

        assertEquals(AuthFailureReason.INVALID_INPUT, failure.reason)
        assertEquals(null, repository.email)
    }

    @Test
    fun `rejects blank password before network call`() {
        val repository = RecordingAuthRepository()

        val failure = assertThrows(AuthFailure::class.java) {
            runBlocking { LoginUseCase(repository)("worker@example.com", "  ") }
        }

        assertEquals(AuthFailureReason.INVALID_INPUT, failure.reason)
        assertEquals(null, repository.email)
    }

    private class RecordingAuthRepository : AuthRepository {
        override val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var email: String? = null
        var password: String? = null

        override suspend fun restoreSession() = Unit
        override suspend fun signIn(email: String, password: String) {
            this.email = email
            this.password = password
        }
        override suspend fun signOut() = Unit
        override suspend fun expireSession() = Unit
    }
}
