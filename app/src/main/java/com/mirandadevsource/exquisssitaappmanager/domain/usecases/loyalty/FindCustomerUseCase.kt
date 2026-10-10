package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class FindCustomerUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend fun byQr(qrPayload: String): Result<Customer?> =
        loyaltyRepository.getCustomerByQr(qrPayload)

    suspend fun byPhone(phone: String): Result<Customer?> =
        loyaltyRepository.getCustomerByPhone(phone)
}
