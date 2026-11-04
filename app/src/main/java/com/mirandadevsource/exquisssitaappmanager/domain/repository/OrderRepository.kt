package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderDetail
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OpenOrderRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.AddOrderItemRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderPageFilter
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOrders(): Flow<List<Order>>
    fun observeOrdersForTable(tableId: String): Flow<List<Order>>
    fun observeOrder(orderId: String): Flow<Order?>
    fun observeOrderDetails(orderId: String): Flow<List<OrderDetail>>
    suspend fun refreshOrders(): Result<Unit>
    suspend fun getOrderPage(
        filter: OrderPageFilter = OrderPageFilter(),
        page: PageRequest = PageRequest(),
    ): Result<DataPage<Order>>
    suspend fun openOrder(request: OpenOrderRequest): Result<String>
    suspend fun addItem(request: AddOrderItemRequest): Result<String>
    suspend fun updateItemQuantity(detailId: String, quantity: Int): Result<String>
    suspend fun cancelItem(detailId: String): Result<String>
    suspend fun cancelOrder(orderId: String): Result<String>
    suspend fun syncPendingCommands(): Result<Unit> = syncPendingCommands(force = false)
    suspend fun syncPendingCommands(force: Boolean): Result<Unit> = Result.success(Unit)
    suspend fun cleanBlockedCommands(): Result<Int> = Result.success(0)
}
