package com.mirandadevsource.exquisssitaappmanager.core.database.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cash_cut_cache",
    indices = [Index(value = ["businessDate", "cutNumber"], unique = true)],
)
data class DailyClosureEntity(
    @PrimaryKey val id: String,
    val businessDate: String,
    val cutNumber: Int,
    val periodStart: String,
    val periodEnd: String,
    val cashCents: Long,
    val cardCents: Long,
    val otherCents: Long,
    val totalCents: Long,
    val closedOrderCount: Int,
    val closedAt: String,
    val closedBy: String?,
    val syncVersion: Long,
    val cachedAtMillis: Long = System.currentTimeMillis(),
)
