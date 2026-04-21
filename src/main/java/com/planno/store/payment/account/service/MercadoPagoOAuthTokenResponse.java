package com.planno.store.payment.account.service;

import java.time.OffsetDateTime;

public record MercadoPagoOAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String scope,
        String userId,
        String publicKey,
        OffsetDateTime expiresAt
) {
}
