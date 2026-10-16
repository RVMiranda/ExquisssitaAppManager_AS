package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.DailyClosureEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.DailyClosureDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DailyClosure

fun DailyClosureDto.toEntity() = DailyClosureEntity(
    id = id,
    businessDate = businessDate,
    cutNumber = cutNumber,
    periodStart = periodStart,
    periodEnd = periodEnd,
    cashCents = cashCents,
    cardCents = cardCents,
    otherCents = otherCents,
    totalCents = totalCents,
    closedOrderCount = closedOrderCount,
    closedAt = closedAt,
    closedBy = closedBy,
    syncVersion = syncVersion,
)

fun DailyClosureEntity.toDomain() = DailyClosure(
    id = id,
    businessDate = businessDate,
    cutNumber = cutNumber,
    periodStart = periodStart,
    periodEnd = periodEnd,
    cashCents = cashCents,
    cardCents = cardCents,
    otherCents = otherCents,
    totalCents = totalCents,
    closedOrderCount = closedOrderCount,
    closedAt = closedAt,
    closedBy = closedBy,
    syncVersion = syncVersion,
)

fun DailyClosureDto.toDomain() = toEntity().toDomain()
