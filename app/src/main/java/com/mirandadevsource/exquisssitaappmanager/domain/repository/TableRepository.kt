package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Table
import kotlinx.coroutines.flow.Flow

interface TableRepository {
    fun observeTables(): Flow<List<Table>>
    suspend fun refreshTables(): Result<Unit>
    suspend fun createTable(number: String): Result<String>
    suspend fun setReservation(tableId: String, reserved: Boolean): Result<String>
    suspend fun syncPendingCommands(): Result<Unit> = syncPendingCommands(force = false)
    suspend fun syncPendingCommands(force: Boolean): Result<Unit> = Result.success(Unit)
}
