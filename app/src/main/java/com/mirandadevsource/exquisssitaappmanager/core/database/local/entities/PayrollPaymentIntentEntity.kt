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
    /** Auth user that requested this payment. Null denotes an unverifiable legacy intent. */
    val actorUserId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastSyncError: String? = null,
)
