package com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyClosureDto(
    val id: String,
    @SerialName("fecha") val businessDate: String,
    @SerialName("numero_corte") val cutNumber: Int,
    @SerialName("periodo_inicio") val periodStart: String,
    @SerialName("periodo_fin") val periodEnd: String,
    @SerialName("total_efectivo") val cashCents: Long,
    @SerialName("total_tarjeta") val cardCents: Long,
    @SerialName("total_otro") val otherCents: Long,
    @SerialName("total_global") val totalCents: Long,
    @SerialName("num_ordenes") val closedOrderCount: Int,
    @SerialName("hora_cierre") val closedAt: String,
    @SerialName("cerrado_por") val closedBy: String? = null,
    @SerialName("sync_version") val syncVersion: Long = 1,
)
