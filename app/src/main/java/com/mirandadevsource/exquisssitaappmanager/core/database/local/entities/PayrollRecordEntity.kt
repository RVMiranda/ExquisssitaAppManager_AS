package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payroll_record_cache",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["empleadoIdRemote", "periodoInicio"]),
        Index(value = ["employeeLocalId"]),
    ],
)
data class PayrollRecordEntity(
    @PrimaryKey val localId: String,
    val remoteId: String,
    val employeeLocalId: String?,
    val empleadoIdRemote: String,
    val montoCents: Long,
    val fechaPago: String,
    val notas: String?,
    val periodoInicio: String?,
    val fechaProgramada: String?,
    val registradoPor: String?,
    val createdAt: String,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
