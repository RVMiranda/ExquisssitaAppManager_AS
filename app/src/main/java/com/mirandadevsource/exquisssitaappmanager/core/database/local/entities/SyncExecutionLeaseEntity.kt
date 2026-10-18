package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_execution_leases")
data class SyncExecutionLeaseEntity(
    @PrimaryKey val actorUserId: String,
    val leaseOwner: String,
    val leaseUntilMillis: Long,
)
