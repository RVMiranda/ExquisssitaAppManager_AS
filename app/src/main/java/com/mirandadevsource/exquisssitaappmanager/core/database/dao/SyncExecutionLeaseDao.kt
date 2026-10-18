package com.mirandadevsource.exquisssitaappmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.SyncExecutionLeaseEntity

@Dao
abstract class SyncExecutionLeaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(lease: SyncExecutionLeaseEntity)

    @Query("DELETE FROM sync_execution_leases WHERE actorUserId = :actorUserId AND leaseUntilMillis <= :nowMillis")
    protected abstract suspend fun deleteExpired(actorUserId: String, nowMillis: Long)

    @Query("SELECT leaseOwner FROM sync_execution_leases WHERE actorUserId = :actorUserId LIMIT 1")
    protected abstract suspend fun ownerFor(actorUserId: String): String?

    @Query("UPDATE sync_execution_leases SET leaseUntilMillis = :leaseUntilMillis WHERE actorUserId = :actorUserId AND leaseOwner = :owner")
    abstract suspend fun renew(actorUserId: String, owner: String, leaseUntilMillis: Long): Int

    @Query("DELETE FROM sync_execution_leases WHERE actorUserId = :actorUserId AND leaseOwner = :owner")
    abstract suspend fun release(actorUserId: String, owner: String): Int

    @Transaction
    open suspend fun acquire(lease: SyncExecutionLeaseEntity, nowMillis: Long): Boolean {
        deleteExpired(lease.actorUserId, nowMillis)
        insertIfAbsent(lease)
        return ownerFor(lease.actorUserId) == lease.leaseOwner
    }
}
