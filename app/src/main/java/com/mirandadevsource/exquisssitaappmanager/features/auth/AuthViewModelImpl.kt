package com.mirandadevsource.exquisssitaappmanager.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AuthState
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailure
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailureReason
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.LoginUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.ObserveAuthStateUseCase
import com.mirandadevsource.exquisssitaappmanager.domain.usecases.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val observeAuthState: ObserveAuthStateUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthUiState())
    private val mutableEvents = MutableSharedFlow<AuthUiEvent>(extraBufferCapacity = 1)
    private var sessionExpiryJob: Job? = null

    val uiState = mutableUiState.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            observeAuthState().collect { authState ->
                when (authState) {
                    AuthState.Checking -> mutableUiState.update {
                        it.copy(stage = AuthUiStage.CHECKING, session = null)
                    }
                    AuthState.SignedOut -> {
                        sessionExpiryJob?.cancel()
                        mutableUiState.update {
                            it.copy(stage = AuthUiStage.SIGNED_OUT, session = null, isSubmitting = false)
                        }
                    }
                    is AuthState.SignedIn -> {
                        val session = authState.session
                        mutableUiState.update {
                            it.copy(
                                stage = if (session.isOffline) AuthUiStage.OFFLINE else AuthUiStage.SIGNED_IN,
                                session = session,
                                password = "",
                                isSubmitting = false,
                                errorMessage = null,
                            )
                        }
                        scheduleExpiry(session.startedAtEpochMillis)
                    }
                }
            }
        }
        viewModelScope.launch {
            runCatching { authRepository.restoreSession() }
                .onFailure { failure ->
                    mutableUiState.update {
                        it.copy(stage = AuthUiStage.SIGNED_OUT, session = null, errorMessage = messageFor(failure))
                    }
                }
        }
    }

    fun onEmailChanged(value: String) = mutableUiState.update {
        it.copy(email = value, errorMessage = null)
    }

    fun onPasswordChanged(value: String) = mutableUiState.update {
        it.copy(password = value, errorMessage = null)
    }

    fun togglePasswordVisibility() = mutableUiState.update {
        it.copy(passwordVisible = !it.passwordVisible)
    }

    fun submit() {
        if (mutableUiState.value.isSubmitting) return
        viewModelScope.launch {
            val current = mutableUiState.value
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                loginUseCase(current.email, current.password)
                mutableEvents.emit(AuthUiEvent.LoginSucceeded)
            } catch (failure: AuthFailure) {
                mutableUiState.update { it.copy(errorMessage = messageFor(failure)) }
            } catch (_: Exception) {
                mutableUiState.update { it.copy(errorMessage = messageFor(null)) }
            } finally {
                mutableUiState.update { it.copy(isSubmitting = false, password = "") }
            }
        }
    }

    fun signOut() {
        sessionExpiryJob?.cancel()
        mutableUiState.update {
            it.copy(
                stage = AuthUiStage.SIGNED_OUT,
                session = null,
                isSubmitting = false,
                password = "",
            )
        }
        viewModelScope.launch {
            runCatching { signOutUseCase() }
                .onFailure { failure ->
                    mutableUiState.update { state -> state.copy(errorMessage = messageFor(failure)) }
                }
        }
    }

    fun checkSessionExpiry() {
        val session = mutableUiState.value.session ?: return
        if (System.currentTimeMillis() - session.startedAtEpochMillis >= SESSION_LIMIT_MILLIS) {
            expireSession()
        }
    }

    private fun expireSession() {
        sessionExpiryJob?.cancel()
        mutableUiState.update {
            it.copy(
                stage = AuthUiStage.SIGNED_OUT,
                session = null,
                isSubmitting = false,
                password = "",
                errorMessage = "Tu sesión ha expirado por seguridad (límite de 10 horas). Inicia sesión nuevamente.",
            )
        }
        viewModelScope.launch {
            runCatching { authRepository.expireSession() }
        }
    }

    private fun scheduleExpiry(startedAt: Long) {
        sessionExpiryJob?.cancel()
        val remaining = (startedAt + SESSION_LIMIT_MILLIS - System.currentTimeMillis()).coerceAtLeast(0)
        sessionExpiryJob = viewModelScope.launch {
            delay(remaining)
            expireSession()
        }
    }

    private fun messageFor(failure: Throwable?): String = when ((failure as? AuthFailure)?.reason) {
        AuthFailureReason.CONFIGURATION -> "Falta configurar la conexión con Supabase."
        AuthFailureReason.INVALID_INPUT -> "Escribe un correo válido y una contraseña."
        AuthFailureReason.INVALID_CREDENTIALS -> "El correo o la contraseña no son correctos."
        AuthFailureReason.NETWORK -> "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
        AuthFailureReason.NOT_AUTHORIZED -> "La cuenta no tiene acceso activo. Contacta al administrador."
        AuthFailureReason.UNKNOWN, null -> "No se pudo iniciar sesión. Inténtalo de nuevo."
    }

    private companion object {
        const val SESSION_LIMIT_MILLIS = 10L * 60L * 60L * 1000L
    }
}
