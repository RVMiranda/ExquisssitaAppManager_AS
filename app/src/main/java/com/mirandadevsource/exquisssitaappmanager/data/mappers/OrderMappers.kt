package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.dao.TableSummaryRow
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderDetailEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.OrderEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.TableEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.OrderDetailDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.OrderDto
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.TableDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.*

fun TableDto.toEntity() = TableEntity(id, numeroMesa, estado, createdAt, updatedAt, syncVersion)
fun TableSummaryRow.toDomain() = Table(id, numeroMesa, derivedTableStatus(estado, openOrderCount), openOrderCount, createdAt, updatedAt, syncVersion, syncState != TableEntity.SYNCED)
fun TableEntity.toDomain(openOrders: Int = 0) = Table(id, numeroMesa, derivedTableStatus(estado, openOrders), openOrders, createdAt, updatedAt, syncVersion, syncState != TableEntity.SYNCED)
fun OrderDto.toEntity(actorUserId: String) = OrderEntity(id, mesaId, clienteId, tipoServicio, estado, fechaApertura, fechaCierre, notas, createdAt, updatedAt, syncVersion, actorUserId)
fun OrderEntity.toDomain() = Order(id, mesaId, clienteId, ServiceType.fromDatabase(tipoServicio), OrderStatus.fromDatabase(estado), fechaApertura, fechaCierre, notas, createdAt, updatedAt, syncVersion, syncState != OrderEntity.SYNCED)
fun OrderDetailDto.toEntity() = OrderDetailEntity(id, ordenId, productoId, cantidad, precioUnitarioCentavos, estadoPago, notas, createdAt, updatedAt, syncVersion)
fun OrderDetailEntity.toDomain() = OrderDetail(id, ordenId, productoId, cantidad, precioUnitarioCentavos, PaymentLineStatus.fromDatabase(estadoPago), notas, createdAt, updatedAt, syncVersion, syncState != OrderDetailEntity.SYNCED)

private fun derivedTableStatus(persisted: String, openOrders: Int) = when {
    openOrders > 0 -> TableStatus.OCCUPIED
    persisted.equals(TableStatus.RESERVED.databaseValue, true) -> TableStatus.RESERVED
    else -> TableStatus.FREE
}
