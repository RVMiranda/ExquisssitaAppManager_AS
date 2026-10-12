package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class OrderStatus(val databaseValue: String) {
    OPEN("Abierta"), CLOSED("Cerrada"), CANCELLED("Cancelada");

    companion object {
        fun fromDatabase(value: String): OrderStatus =
            entries.firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: OPEN
    }
}

enum class ServiceType(val databaseValue: String) {
    TABLE("Mesa"), TAKEOUT("Para llevar"), DELIVERY("Domicilio");

    companion object {
        fun fromDatabase(value: String): ServiceType =
            entries.firstOrNull { it.databaseValue.equals(value, ignoreCase = true) } ?: TABLE
    }
}

data class Order(
    val id: String,
    val tableId: String?,
    val customerId: String?,
    val serviceType: ServiceType,
    val status: OrderStatus,
    val openedAt: String,
    val closedAt: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
    val isPendingSync: Boolean = false,
)

data class OpenOrderRequest(
    val tableId: String? = null,
    val customerId: String? = null,
    val serviceType: ServiceType,
    val notes: String? = null,
)

data class AddOrderItemRequest(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val notes: String? = null,
)
