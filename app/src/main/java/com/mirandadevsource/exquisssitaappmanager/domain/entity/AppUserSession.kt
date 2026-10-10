package com.mirandadevsource.exquisssitaappmanager.domain.entity

enum class UserRole(val databaseValue: String, val displayName: String) {
    ADMIN("admin", "Administrador"),
    GERENTE("gerente", "Gerente"),
    CAJERO("cajero", "Cajero"),
    MESERO("mesero", "Mesero"),
    EMPLEADO("empleado", "Empleado");

    companion object {
        fun fromDatabase(value: String): UserRole? =
            entries.firstOrNull { it.databaseValue == value.trim().lowercase() }
    }
}

data class AppUserSession(
    val userId: String,
    val email: String,
    val role: UserRole,
    val employeeId: String,
    val employeeName: String,
    val startedAtEpochMillis: Long,
    val isOffline: Boolean = false,
)

sealed interface AuthState {
    data object Checking : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val session: AppUserSession) : AuthState
}
