package com.planno.store.payment.account.dto;

import java.util.UUID;

public record OAuthCallbackResponse(
        UUID storeId,
        String provider,
        String connectionStatus,
        boolean readyForCheckout,
        boolean readyForSplit,
        boolean readyForTransparentCheckout
) {
}
