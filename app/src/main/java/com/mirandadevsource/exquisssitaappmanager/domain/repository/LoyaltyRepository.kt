package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CreatedCustomer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.CustomerVisit

interface LoyaltyRepository {
    /** [qrPayload] is the raw token scanned from the customer's QR. */
    suspend fun getCustomerByQr(qrPayload: String): Result<Customer?>

    suspend fun getCustomerByPhone(phone: String): Result<Customer?>

    /** Records at most one visit per customer and Mexico City business date. */
    suspend fun registerDailyVisit(qrPayload: String): Result<CustomerVisit>

    /** The returned QR payload is shown once; only its SHA-256 digest is stored remotely. */
    suspend fun createCustomer(name: String, phone: String?): Result<CreatedCustomer>
}
