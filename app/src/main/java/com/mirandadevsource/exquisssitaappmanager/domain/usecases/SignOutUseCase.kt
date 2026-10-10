package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.signOut()
}
