package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class TableStatus(val databaseValue: String) {
    FREE("Libre"),
    RESERVED("Reservada"),
    OCCUPIED("Ocupada");

    companion object {
        fun fromDatabase(value: String): TableStatus =
            entries.firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: FREE
    }
}

data class Table(
    val id: String,
    val number: String,
    /** Persisted reservation status. Occupancy is derived from open orders. */
    val status: TableStatus,
    val openOrderCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val isPendingSync: Boolean = false,
)
