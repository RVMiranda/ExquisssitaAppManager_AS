package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailure
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthFailureReason
import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        val normalizedEmail = email.trim()
        val isEmailValid = EMAIL_PATTERN.matches(normalizedEmail)
        if (!isEmailValid || password.isBlank()) {
            throw AuthFailure(AuthFailureReason.INVALID_INPUT)
        }
        authRepository.signIn(normalizedEmail, password)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}
