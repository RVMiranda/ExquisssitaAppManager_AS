package com.mirandadevsource.exquisssitaappmanager.domain.usecases.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CreatedCustomer
import com.mirandadevsource.exquisssitaappmanager.domain.repository.LoyaltyRepository
import javax.inject.Inject

class CreateCustomerUseCase @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
) {
    suspend operator fun invoke(name: String, phone: String?): Result<CreatedCustomer> {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre del cliente es obligatorio."))
        }
        val normalizedPhone = phone?.trim()?.ifBlank { null }
        return loyaltyRepository.createCustomer(normalizedName, normalizedPhone)
    }
}
