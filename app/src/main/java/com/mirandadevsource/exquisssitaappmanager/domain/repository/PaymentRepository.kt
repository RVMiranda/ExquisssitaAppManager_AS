package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Payment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentCommand
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PaymentRequest
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun observePayments(orderId: String): Flow<List<Payment>>
    suspend fun processPayment(request: PaymentRequest): Result<PaymentCommand>
    suspend fun syncPendingPayments(): Result<Unit>
}
