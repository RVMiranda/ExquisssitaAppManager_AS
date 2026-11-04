package com.mirandadevsource.exquisssitaappmanager.domain.entity

data class Product(
    val id: String,
    val nombre: String,
    /** Price stored by Supabase in centavos. */
    val precioCentavos: Long,
    val categoria: String,
    val controlaInventario: Boolean,
    val precioAbierto: Boolean = false,
    val stockActual: Int?,
    val stockMinimo: Int?,
    val activo: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val syncVersion: Long,
)
