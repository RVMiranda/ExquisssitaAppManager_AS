package com.mirandadevsource.exquisssitaappmanager.data.sync

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.SyncExecutionLeaseDao
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.SyncExecutionLeaseEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Serializes local outbox replay in-process and coordinates owners through a durable Room lease. */
internal object SyncExecutionMutex {
    val mutex = Mutex()

    suspend fun withActorLease(
        actorUserId: String,
        leaseDao: SyncExecutionLeaseDao,
        block: suspend () -> Unit,
    ): Boolean = mutex.withLock {
        val owner = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val acquired = leaseDao.acquire(
            SyncExecutionLeaseEntity(actorUserId, owner, now + LEASE_DURATION_MILLIS),
            now,
        )
        if (!acquired) return@withLock false

        try {
            coroutineScope {
                val work = async { block() }
                val heartbeat = launch {
                    while (true) {
                        delay(HEARTBEAT_INTERVAL_MILLIS)
                        val renewed = leaseDao.renew(
                            actorUserId,
                            owner,
                            System.currentTimeMillis() + LEASE_DURATION_MILLIS,
                        )
                        if (renewed != 1) {
                            work.cancel(LeaseLostException())
                            break
                        }
                    }
                }
                try {
                    work.await()
                } finally {
                    heartbeat.cancelAndJoin()
                }
            }
            true
        } finally {
            leaseDao.release(actorUserId, owner)
        }
    }

    private class LeaseLostException : CancellationException("Se perdió el lease durable de sincronización.")

    private const val LEASE_DURATION_MILLIS = 120_000L
    private const val HEARTBEAT_INTERVAL_MILLIS = 20_000L
}
