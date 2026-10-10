package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.EmployeeEntity

@Dao
interface EmployeeDao {
    @Query(
        "SELECT * FROM employee_cache WHERE (:includeInactive = 1 OR activo = 1) " +
            "ORDER BY nombre COLLATE NOCASE, apellido COLLATE NOCASE",
    )
    suspend fun getEmployees(includeInactive: Boolean): List<EmployeeEntity>

    @Query("SELECT * FROM employee_cache WHERE localId = :id LIMIT 1")
    suspend fun getByLocalId(id: String): EmployeeEntity?

    @Query("SELECT * FROM employee_cache WHERE remoteId = :id LIMIT 1")
    suspend fun getByRemoteId(id: String): EmployeeEntity?

    @Query("SELECT * FROM employee_cache WHERE syncState IN ('PENDING_UPDATE', 'SYNC_ERROR') ORDER BY localUpdatedAtMillis")
    suspend fun getPendingUpdates(): List<EmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: EmployeeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(employees: List<EmployeeEntity>)
}
