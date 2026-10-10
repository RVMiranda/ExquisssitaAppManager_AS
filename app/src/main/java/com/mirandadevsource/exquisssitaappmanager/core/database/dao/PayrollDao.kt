package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity

@Dao
interface PayrollDao {
    @Query("SELECT * FROM payroll_record_cache WHERE periodoInicio = :periodoInicio ORDER BY fechaPago DESC")
    suspend fun getPaymentsForPeriod(periodoInicio: String): List<PayrollRecordEntity>

    @Query("SELECT * FROM payroll_record_cache WHERE empleadoIdRemote = :empleadoId ORDER BY periodoInicio DESC, fechaPago DESC")
    suspend fun getPaymentHistory(empleadoId: String): List<PayrollRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: PayrollRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PayrollRecordEntity>)

    @Query("SELECT * FROM payroll_payment_intents WHERE periodoInicio = :periodoInicio ORDER BY createdAtMillis")
    suspend fun getPaymentIntentsForPeriod(periodoInicio: String): List<PayrollPaymentIntentEntity>

    @Query("SELECT * FROM payroll_payment_intents ORDER BY createdAtMillis")
    suspend fun getPendingPaymentIntents(): List<PayrollPaymentIntentEntity>

    @Query("SELECT * FROM payroll_payment_intents WHERE employeeLocalId = :employeeId AND periodoInicio = :periodoInicio LIMIT 1")
    suspend fun getPaymentIntent(employeeId: String, periodoInicio: String): PayrollPaymentIntentEntity?

    @Query("SELECT * FROM payroll_payment_intents WHERE employeeLocalId = :employeeId ORDER BY createdAtMillis DESC")
    suspend fun getPaymentIntentsForEmployee(employeeId: String): List<PayrollPaymentIntentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentIntent(intent: PayrollPaymentIntentEntity)

    @Query("DELETE FROM payroll_payment_intents WHERE intentId = :intentId")
    suspend fun deletePaymentIntent(intentId: String)

    @Query("UPDATE payroll_payment_intents SET lastSyncError = :error WHERE intentId = :intentId")
    suspend fun setPaymentIntentError(intentId: String, error: String?)
}
