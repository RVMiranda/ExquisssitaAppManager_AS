package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentDetailEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PaymentStockEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_cache WHERE ordenId = :orderId ORDER BY fecha, id")
    fun observeForOrder(orderId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transaction_cache WHERE ordenId = :orderId ORDER BY fecha, id")
    suspend fun getForOrder(orderId: String): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(montoCentavos), 0) FROM transaction_cache WHERE ordenId = :orderId")
    suspend fun getPaidAmount(orderId: String): Long

    @Query("SELECT * FROM transaction_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM payment_detail_cache WHERE transaccionId IN (:transactionIds)")
    suspend fun getPaymentDetails(transactionIds: List<String>): List<PaymentDetailEntity>

    @Query("SELECT * FROM payment_stock_cache WHERE transaccionId IN (:transactionIds)")
    suspend fun getPaymentStock(transactionIds: List<String>): List<PaymentStockEntity>

    @Query("SELECT COUNT(*) FROM payment_detail_cache WHERE detalleOrdenId = :detailId")
    suspend fun countAllocationsForDetail(detailId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(rows: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentDetails(rows: List<PaymentDetailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentStock(rows: List<PaymentStockEntity>)
}
