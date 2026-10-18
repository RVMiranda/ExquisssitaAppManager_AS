package com.mirandadevsource.exquisssitaappmanager.domain.usecases.orders

import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Order
import com.mirandadevsource.exquisssitaappmanager.domain.entity.OrderPageFilter
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.repository.OrderRepository
import javax.inject.Inject

class GetOrdersPageUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
) {
    suspend operator fun invoke(
        filter: OrderPageFilter = OrderPageFilter(),
        page: PageRequest = PageRequest(),
    ): Result<DataPage<Order>> = orderRepository.getOrderPage(filter, page)
}
