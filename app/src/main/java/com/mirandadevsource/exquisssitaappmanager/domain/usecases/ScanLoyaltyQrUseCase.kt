package com.mirandadevsource.exquisssitaappmanager.domain.usecases

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class ScanLoyaltyQrUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(qrPayload: String): Result<Customer?> =
        loyaltyRepository.getCustomerByQr(qrPayload)
}
