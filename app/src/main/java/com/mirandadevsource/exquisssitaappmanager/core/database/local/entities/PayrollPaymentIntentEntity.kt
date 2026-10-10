package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payroll_payment_intents",
    indices = [Index(value = ["employeeLocalId", "periodoInicio"], unique = true)],
)
data class PayrollPaymentIntentEntity(
    @PrimaryKey val intentId: String,
    val employeeLocalId: String,
    val periodoInicio: String,
    val requestedFechaPago: String?,
    val notas: String?,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastSyncError: String? = null,
)
