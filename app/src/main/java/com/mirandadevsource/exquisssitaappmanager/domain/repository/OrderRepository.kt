package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OpenOrderRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AddOrderItemRequest
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOrders(): Flow<List<Order>>
    fun observeOrdersForTable(tableId: String): Flow<List<Order>>
    fun observeOrder(orderId: String): Flow<Order?>
    fun observeOrderDetails(orderId: String): Flow<List<OrderDetail>>
    suspend fun refreshOrders(): Result<Unit>
    suspend fun openOrder(request: OpenOrderRequest): Result<String>
    suspend fun addItem(request: AddOrderItemRequest): Result<String>
    suspend fun updateItemQuantity(detailId: String, quantity: Int): Result<String>
    suspend fun cancelItem(detailId: String): Result<String>
    suspend fun cancelOrder(orderId: String): Result<String>
    suspend fun syncPendingCommands(): Result<Unit>
}
