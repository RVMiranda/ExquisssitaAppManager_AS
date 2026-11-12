package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha ORDER BY createdAt DESC, localId DESC")
    suspend fun getExpensesForDate(actorUserId: String, fecha: String): List<ExpenseEntity>

    @Query("SELECT * FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha ORDER BY createdAt DESC, localId DESC")
    fun getExpensesForDateFlow(actorUserId: String, fecha: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha BETWEEN :startDate AND :endDate ORDER BY fecha DESC, createdAt DESC")
    suspend fun getExpensesForDateRange(actorUserId: String, startDate: String, endDate: String): List<ExpenseEntity>

    @Query("SELECT * FROM expense_cache WHERE localId = :id LIMIT 1")
    suspend fun getByLocalId(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense_cache WHERE remoteId = :remoteId AND cacheActorUserId = :actorUserId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String, actorUserId: String): ExpenseEntity?

    @Query("SELECT * FROM expense_cache WHERE syncState IN ('PENDING_INSERT', 'PENDING_UPDATE', 'PENDING_DELETE', 'SYNC_ERROR') AND cacheActorUserId = :actorUserId ORDER BY localUpdatedAtMillis")
    suspend fun getPendingSync(actorUserId: String): List<ExpenseEntity>

    @Query("SELECT COALESCE(SUM(montoCents), 0) FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha AND syncState != 'PENDING_DELETE'")
    suspend fun getTotalExpensesCentsForDate(actorUserId: String, fecha: String): Long

    @Query("SELECT COALESCE(SUM(montoCents), 0) FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha AND metodoPago = :metodoPago AND syncState != 'PENDING_DELETE'")
    suspend fun getExpensesCentsByPaymentMethod(actorUserId: String, fecha: String, metodoPago: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expense_cache WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM expense_cache WHERE remoteId = :remoteId AND cacheActorUserId = :actorUserId")
    suspend fun deleteByRemoteId(remoteId: String, actorUserId: String)

    @Query("DELETE FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha AND syncState = 'SYNCED' AND remoteId NOT IN (:remoteIds)")
    suspend fun deleteSyncedNotIn(actorUserId: String, fecha: String, remoteIds: List<String>)

    @Query("DELETE FROM expense_cache WHERE cacheActorUserId = :actorUserId AND fecha = :fecha AND syncState = 'SYNCED'")
    suspend fun deleteAllSyncedForDate(actorUserId: String, fecha: String)

    @Transaction
    suspend fun replaceDate(actorUserId: String, fecha: String, expenses: List<ExpenseEntity>) {
        deleteAllSyncedForDate(actorUserId, fecha)
        upsertAll(expenses)
    }
}
