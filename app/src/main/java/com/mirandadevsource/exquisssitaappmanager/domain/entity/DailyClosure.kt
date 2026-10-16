package com.mirandadevsource.exquisssitaappmanager.domain.entity

/** One immutable cash cut covering sales since the preceding cut. Money is stored in MXN cents. */
data class DailyClosure(
    val id: String,
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
)

data class CashRegisterDaySummary(
    val businessDate: String,
    val cutCount: Int,
    val cashCents: Long,
    val cardCents: Long,
    val otherCents: Long,
    val totalCents: Long,
    val closedOrderCount: Int,
)
