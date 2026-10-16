package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.DailyClosureEntity

@Dao
interface DailyClosureDao {
    @Query("SELECT * FROM cash_cut_cache WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DailyClosureEntity?

    @Query("SELECT * FROM cash_cut_cache WHERE businessDate = :date ORDER BY cutNumber")
    suspend fun getCutsForDay(date: String): List<DailyClosureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cuts: List<DailyClosureEntity>)

    @Query("DELETE FROM cash_cut_cache WHERE businessDate = :date")
    suspend fun deleteForDay(date: String)

    @Transaction
    suspend fun replaceDay(date: String, cuts: List<DailyClosureEntity>) {
        deleteForDay(date)
        upsertAll(cuts)
    }

    @Query("""
        SELECT businessDate AS businessDate,
               COUNT(*) AS cutCount,
               COALESCE(SUM(cashCents), 0) AS cashCents,
               COALESCE(SUM(cardCents), 0) AS cardCents,
               COALESCE(SUM(otherCents), 0) AS otherCents,
               COALESCE(SUM(totalCents), 0) AS totalCents,
               COALESCE(SUM(closedOrderCount), 0) AS closedOrderCount
        FROM cash_cut_cache WHERE businessDate = :date GROUP BY businessDate
    """)
    suspend fun getDaySummary(date: String): CashRegisterDaySummaryRow?
}

data class CashRegisterDaySummaryRow(
    val businessDate: String,
    val cutCount: Int,
    val cashCents: Long,
    val cardCents: Long,
    val otherCents: Long,
    val totalCents: Long,
    val closedOrderCount: Int,
)
