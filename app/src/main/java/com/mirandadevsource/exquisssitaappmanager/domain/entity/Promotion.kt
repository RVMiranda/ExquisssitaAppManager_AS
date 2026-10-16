package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class Promotion(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val terminosCondiciones: String?,
    val imagenUrl: String?,
    val fechaPublicacion: String,
    val fechaVencimiento: String?,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
)
