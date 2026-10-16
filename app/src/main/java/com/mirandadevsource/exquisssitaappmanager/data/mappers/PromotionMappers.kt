package com.mirandadevsource.exquisssitaappmanager.data.mappers

import com.mirandadevsource.exquisssitaappmanager.core.database.local.entities.PromotionEntity
import com.mirandadevsource.exquisssitaappmanager.core.database.remote.dto.PromotionDto
import com.mirandadevsource.exquisssitaappmanager.domain.entity.Promotion

fun PromotionDto.toEntity(): PromotionEntity = PromotionEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    terminosCondiciones = terminosCondiciones,
    imagenUrl = imagenUrl,
    fechaPublicacion = fechaPublicacion,
    fechaVencimiento = fechaVencimiento,
    activo = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
)

fun PromotionEntity.toDomain(): Promotion = Promotion(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    terminosCondiciones = terminosCondiciones,
    imagenUrl = imagenUrl,
    fechaPublicacion = fechaPublicacion,
    fechaVencimiento = fechaVencimiento,
    activo = activo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncVersion = syncVersion,
)
