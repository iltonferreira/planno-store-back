package com.planno.store.payment.account.dto;

public record OAuthConnectionResponse(
        String provider,
        String authorizationUrl
) {
}
