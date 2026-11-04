package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.ProductEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.ProductDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Product

fun ProductDto.toEntity(): ProductEntity = ProductEntity(
    id = id,
    nombre = nombre,
    precioCentavos = precio,
    categoria = categoria,
    controlaInventario = controlaInventario,
    precioAbierto = precioAbierto,
    stockActual = stockActual,
    stockMinimo = stockMinimo,
    activo = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
)

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    nombre = nombre,
    precioCentavos = precioCentavos,
    categoria = categoria,
    controlaInventario = controlaInventario,
    precioAbierto = precioAbierto,
    stockActual = stockActual,
    stockMinimo = stockMinimo,
    activo = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
)
