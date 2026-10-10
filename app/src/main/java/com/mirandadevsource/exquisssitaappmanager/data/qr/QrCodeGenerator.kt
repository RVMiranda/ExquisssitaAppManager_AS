package com.mirandadevsource.exquisssitaappmanager.data.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encodes the immutable payload returned by customer creation as a QR matrix. */
object QrCodeGenerator {
    fun createMatrix(payload: String, size: Int = DEFAULT_SIZE): BitMatrix {
        require(payload.isNotBlank()) { "El contenido del código QR es obligatorio." }
        require(size >= MIN_SIZE) { "El tamaño del código QR debe ser de al menos $MIN_SIZE píxeles." }
        return MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }

    private const val DEFAULT_SIZE = 512
    private const val MIN_SIZE = 128
}
