package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.repository.AuthRepository
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke() = authRepository.state
}
