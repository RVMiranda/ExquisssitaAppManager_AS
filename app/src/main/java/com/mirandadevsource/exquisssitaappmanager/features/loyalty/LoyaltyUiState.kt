package com.mirandadevsource.exquisssitaappmanager.features.loyalty

import com.mirandadevsource.exquisssitaappmanager.domain.entity.Customer
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RedeemableReward
import com.mirandadevsource.exquisssitaappmanager.domain.entity.RewardPeriod

data class LoyaltyUiState(
    val selectedTab: Int = 0, // 0 = Escanear QR, 1 = Búsqueda por Teléfono
    val phoneInput: String = "",
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
    // Estado para flujo de Canje de Recompensas
    val isRedeemDialogOpen: Boolean = false,
    val isLoadingRedeemRewards: Boolean = false,
    val redeemableRewards: List<RedeemableReward> = emptyList(),
    val selectedRewardForRedemption: RedeemableReward? = null,
    val isExecutingRedemption: Boolean = false,
    val redemptionError: String? = null,
    val activePeriodLabel: String = RewardPeriod.current().displayLabel(),
) {
    val canSearchPhone: Boolean
        get() = phoneInput.length in 7..10 && phoneInput.all(Char::isDigit) && !isSearching

    val canRegisterVisit: Boolean
        get() = isOnline && hasScannedQrToken && customer != null && !isRegisteringVisit && !(customer.isCached)

    val canOpenRedemption: Boolean
        get() = isOnline && hasScannedQrToken && customer != null && !(customer.isCached) && !isRegisteringVisit && !isExecutingRedemption
}
