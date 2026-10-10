package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class RegisterCustomerVisitUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(qrPayload: String): Result<CustomerVisit> {
        if (qrPayload.length !in MIN_QR_TOKEN_LENGTH..MAX_QR_TOKEN_LENGTH) {
            return Result.failure(IllegalArgumentException("El código QR no es válido."))
        }
        return loyaltyRepository.registerDailyVisit(qrPayload)
    }

    private companion object {
        const val MIN_QR_TOKEN_LENGTH = 32
        const val MAX_QR_TOKEN_LENGTH = 512
    }
}
