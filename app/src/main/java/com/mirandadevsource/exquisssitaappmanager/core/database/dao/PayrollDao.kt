package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollPaymentIntentEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PayrollRecordEntity

@Dao
interface PayrollDao {
    @Query("SELECT * FROM payroll_record_cache WHERE periodoInicio = :periodoInicio AND cacheActorUserId = :actorUserId ORDER BY fechaPago DESC")
    suspend fun getPaymentsForPeriod(periodoInicio: String, actorUserId: String): List<PayrollRecordEntity>

    @Query("SELECT * FROM payroll_record_cache WHERE empleadoIdRemote = :empleadoId AND cacheActorUserId = :actorUserId ORDER BY periodoInicio DESC, fechaPago DESC")
    suspend fun getPaymentHistory(empleadoId: String, actorUserId: String): List<PayrollRecordEntity>

    @Query("SELECT * FROM payroll_record_cache WHERE empleadoIdRemote = :empleadoId AND cacheActorUserId = :actorUserId ORDER BY periodoInicio DESC, fechaPago DESC, localId DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaymentHistoryPage(empleadoId: String, actorUserId: String, limit: Int, offset: Int): List<PayrollRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: PayrollRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PayrollRecordEntity>)

    @Query("SELECT * FROM payroll_payment_intents WHERE periodoInicio = :periodoInicio AND actorUserId = :actorUserId ORDER BY createdAtMillis")
    suspend fun getPaymentIntentsForPeriod(periodoInicio: String, actorUserId: String): List<PayrollPaymentIntentEntity>

    @Query("SELECT * FROM payroll_payment_intents WHERE actorUserId = :actorUserId ORDER BY createdAtMillis")
    suspend fun getPendingPaymentIntents(actorUserId: String): List<PayrollPaymentIntentEntity>

    @Query("SELECT * FROM payroll_payment_intents WHERE employeeLocalId = :employeeId AND periodoInicio = :periodoInicio LIMIT 1")
    suspend fun getPaymentIntent(employeeId: String, periodoInicio: String): PayrollPaymentIntentEntity?

    @Query("SELECT * FROM payroll_payment_intents WHERE employeeLocalId = :employeeId AND actorUserId = :actorUserId ORDER BY createdAtMillis DESC")
    suspend fun getPaymentIntentsForEmployee(employeeId: String, actorUserId: String): List<PayrollPaymentIntentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentIntent(intent: PayrollPaymentIntentEntity)

    @Query("DELETE FROM payroll_payment_intents WHERE intentId = :intentId")
    suspend fun deletePaymentIntent(intentId: String)

    @Query("UPDATE payroll_payment_intents SET lastSyncError = :error WHERE intentId = :intentId")
    suspend fun setPaymentIntentError(intentId: String, error: String?)
}
