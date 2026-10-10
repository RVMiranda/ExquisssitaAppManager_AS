package com.mirandadevsource.exquisssitaappmanager.features.auth

import com.mirandadevsource.exquisssitaappmanager.domain.entity.AppUserSession

enum class AuthUiStage { CHECKING, SIGNED_OUT, SIGNED_IN, OFFLINE }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val stage: AuthUiStage = AuthUiStage.CHECKING,
    val session: AppUserSession? = null,
    val errorMessage: String? = null,
)

sealed interface AuthUiEvent {
    data object LoginSucceeded : AuthUiEvent
}
