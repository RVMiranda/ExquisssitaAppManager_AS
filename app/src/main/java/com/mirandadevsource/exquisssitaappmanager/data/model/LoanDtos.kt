package com.mirandadevsource.exquisssitaappmanager.data.model

import com.mirandadevsource.exquisssitaappmanager.domain.entity.EmployeeLoan
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanPayment
import com.mirandadevsource.exquisssitaappmanager.domain.entity.LoanStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmployeeLoanDto(
    val id: String,
    @SerialName("empleado_id") val empleadoId: String,
    @SerialName("monto_centavos") val montoCentavos: Long,
    @SerialName("saldo_pendiente_centavos") val saldoPendienteCentavos: Long,
    @SerialName("fecha_prestamo") val fechaPrestamo: String,
    val notas: String? = null,
    val estado: String = "pendiente",
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain(employeeName: String? = null): EmployeeLoan = EmployeeLoan(
        id = id,
        empleadoId = empleadoId,
        empleadoNombre = employeeName,
        montoCentavos = montoCentavos,
        saldoPendienteCentavos = saldoPendienteCentavos,
        fechaPrestamo = fechaPrestamo,
        notas = notas,
        estado = LoanStatus.fromDatabase(estado),
        registradoPor = registradoPor,
        createdAt = createdAt.orEmpty(),
        updatedAt = updatedAt.orEmpty(),
    )
}

@Serializable
data class LoanPaymentDto(
    val id: String,
    @SerialName("prestamo_id") val prestamoId: String,
    @SerialName("monto_centavos") val montoCentavos: Long,
    @SerialName("saldo_anterior_centavos") val saldoAnteriorCentavos: Long,
    @SerialName("saldo_restante_centavos") val saldoRestanteCentavos: Long,
    @SerialName("fecha_abono") val fechaAbono: String,
    val tipo: String = "pago_directo",
    @SerialName("nomina_pago_id") val nominaPagoId: String? = null,
    val notas: String? = null,
    @SerialName("registrado_por") val registradoPor: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain(): LoanPayment = LoanPayment(
        id = id,
        prestamoId = prestamoId,
        montoCentavos = montoCentavos,
        saldoAnteriorCentavos = saldoAnteriorCentavos,
        saldoRestanteCentavos = saldoRestanteCentavos,
        fechaAbono = fechaAbono,
        tipo = tipo,
        nominaPagoId = nominaPagoId,
        notas = notas,
        registradoPor = registradoPor,
        createdAt = createdAt.orEmpty(),
    )
}

@Serializable
data class CreateLoanRpcParams(
    @SerialName("p_empleado_id") val empleadoId: String,
    @SerialName("p_monto_centavos") val montoCentavos: Long,
    @SerialName("p_fecha") val fecha: String? = null,
    @SerialName("p_notas") val notas: String? = null,
)

@Serializable
data class RegisterLoanPaymentRpcParams(
    @SerialName("p_prestamo_id") val prestamoId: String,
    @SerialName("p_monto_centavos") val montoCentavos: Long,
    @SerialName("p_tipo") val tipo: String = "pago_directo",
    @SerialName("p_notas") val notas: String? = null,
)
