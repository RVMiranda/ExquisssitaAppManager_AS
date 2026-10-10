package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee

interface EmployeeRepository {
    suspend fun getEmployees(includeInactive: Boolean = false): Result<List<Employee>>
    suspend fun getEmployeeById(id: String): Result<Employee>
    suspend fun createEmployee(
        nombre: String,
        apellido: String,
        telefono: String?,
        salarioCents: Long,
        diaPago: Int,
    ): Result<Employee>
    suspend fun updateEmployee(
        id: String,
        nombre: String,
        apellido: String,
        telefono: String?,
        salarioCents: Long,
        diaPago: Int,
        activo: Boolean,
    ): Result<Employee>
    suspend fun toggleEmployeeActive(id: String, active: Boolean): Result<Unit>
}
