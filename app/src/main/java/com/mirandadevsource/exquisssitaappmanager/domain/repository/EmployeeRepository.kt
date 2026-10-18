package com.mirandadevsource.exquisssitaappmanager.domain.repository

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Employee
import com.mirandadevsource.exquisssitaappmanager.domain.entity.DataPage
import com.mirandadevsource.exquisssitaappmanager.domain.entity.PageRequest
import com.mirandadevsource.exquisssitaappmanager.domain.entity.pageFromCompleteList

interface EmployeeRepository {
    suspend fun getEmployees(includeInactive: Boolean = false): Result<List<Employee>>
    suspend fun getEmployeePage(includeInactive: Boolean = false, page: PageRequest = PageRequest()): Result<DataPage<Employee>> =
        getEmployees(includeInactive).map { pageFromCompleteList(it, page, "employees:${if (includeInactive) "all" else "active"}") }
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
