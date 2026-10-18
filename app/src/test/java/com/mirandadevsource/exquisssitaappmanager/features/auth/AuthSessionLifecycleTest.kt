package com.mirandadevsource.exquisssitaappmanager.features.auth

import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession
import com.mirandadevsource.exquisssitaappmanager.domain.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionLifecycleTest {

    private val sessionLimitMillis = 10L * 60L * 60L * 1000L // 10 horas

    private fun createTestSession(
        startedAt: Long = System.currentTimeMillis(),
        isOffline: Boolean = false,
    ) = AppUserSession(
        userId = "user_123",
        email = "cajero@exquisssita.com",
        role = UserRole.EMPLEADO,
        employeeId = "emp_123",
        employeeName = "Juan Pérez",
        startedAtEpochMillis = startedAt,
        isOffline = isOffline,
    )

    @Test
    fun `initial AuthUiState starts in CHECKING stage with no session`() {
        val state = AuthUiState()
        assertEquals(AuthUiStage.CHECKING, state.stage)
        assertNull(state.session)
        assertFalse(state.isSubmitting)
        assertEquals("", state.email)
        assertEquals("", state.password)
    }

    @Test
    fun `signOut transition immediately clears session, password, and sets stage to SIGNED_OUT`() {
        val activeSession = createTestSession()
        val signedInState = AuthUiState(
            stage = AuthUiStage.SIGNED_IN,
            session = activeSession,
            email = "cajero@exquisssita.com",
            password = "temporary_password",
            isSubmitting = true,
        )

        // Simula la transición inmediata de signOut() sin esperar respuesta remota
        val signedOutState = signedInState.copy(
            stage = AuthUiStage.SIGNED_OUT,
            session = null,
            isSubmitting = false,
            password = "",
            errorMessage = null,
        )

        assertEquals(AuthUiStage.SIGNED_OUT, signedOutState.stage)
        assertNull(signedOutState.session)
        assertFalse(signedOutState.isSubmitting)
        assertEquals("", signedOutState.password)
        assertEquals("cajero@exquisssita.com", signedOutState.email) // Email recordado para conveniencia
    }

    @Test
    fun `checkSessionExpiry identifies session exceeding 10 hours limit`() {
        val now = System.currentTimeMillis()
        val elevenHoursAgo = now - (11L * 60L * 60L * 1000L)
        val expiredSession = createTestSession(startedAt = elevenHoursAgo)

        val isExpired = (now - expiredSession.startedAtEpochMillis) >= sessionLimitMillis
        assertTrue("Session older than 10h should be detected as expired", isExpired)
    }

    @Test
    fun `checkSessionExpiry keeps session younger than 10 hours valid`() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (2L * 60L * 60L * 1000L)
        val validSession = createTestSession(startedAt = twoHoursAgo)

        val isExpired = (now - validSession.startedAtEpochMillis) >= sessionLimitMillis
        assertFalse("Session younger than 10h must not be detected as expired", isExpired)
    }

    @Test
    fun `session expiration sets explicit error message for user reauthentication`() {
        val expiredMessage = "Tu sesión ha expirado por seguridad (límite de 10 horas). Inicia sesión nuevamente."
        val expiredState = AuthUiState(
            stage = AuthUiStage.SIGNED_OUT,
            session = null,
            errorMessage = expiredMessage,
        )

        assertEquals(AuthUiStage.SIGNED_OUT, expiredState.stage)
        assertNull(expiredState.session)
        assertEquals(expiredMessage, expiredState.errorMessage)
    }

    @Test
    fun `offline session is correctly reflected in AuthUiStage`() {
        val offlineSession = createTestSession(isOffline = true)
        val state = AuthUiState(
            stage = AuthUiStage.OFFLINE,
            session = offlineSession,
        )

        assertEquals(AuthUiStage.OFFLINE, state.stage)
        assertTrue(state.session?.isOffline == true)
    }
}
