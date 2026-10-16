package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.CashRegisterDaySummary
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure

interface CashRegisterRepository {
    /** Creates the next server-confirmed cut. This operation requires a live connection. */
    suspend fun closeCurrentPeriod(): Result<DailyClosure>

    /** Returns cached cuts offline and refreshes the selected business day online. */
    suspend fun getCutsForDay(businessDate: String): Result<List<DailyClosure>>

    suspend fun getDaySummary(businessDate: String): Result<CashRegisterDaySummary>
}
