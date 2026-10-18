package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer

data class LoyaltyUiState(
    val selectedTab: Int = 0, // 0 = Escanear QR, 1 = Búsqueda por Teléfono
    val phoneInput: String = "",
    val qrTokenInput: String = "",
    val isSearching: Boolean = false,
    val isRegisteringVisit: Boolean = false,
    val customer: Customer? = null,
    val hasScannedQrToken: Boolean = false,
    val customerNotFound: Boolean = false,
    val isOnline: Boolean = true,
    val successNotification: String? = null,
    val warningNotification: String? = null,
    val errorNotification: String? = null,
    val rewardNotice: String? = null,
    val notificationEventId: Long = 0L,
) {
    val canSearchPhone: Boolean
        get() = phoneInput.length in 7..10 && phoneInput.all(Char::isDigit) && !isSearching

    val canRegisterVisit: Boolean
        get() = isOnline && hasScannedQrToken && customer != null && !isRegisteringVisit && !(customer.isCached)
}
