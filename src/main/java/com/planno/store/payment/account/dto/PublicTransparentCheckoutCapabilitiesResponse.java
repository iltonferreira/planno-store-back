package com.planno.store.payment.account.dto;

public record PublicTransparentCheckoutCapabilitiesResponse(
        boolean enabled,
        boolean connected,
        boolean ready,
        String provider,
        String publicKey
) {
}
