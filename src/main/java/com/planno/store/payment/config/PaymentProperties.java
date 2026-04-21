package com.planno.store.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String credentialsEncryptionKeyBase64,
        OAuthProperties oauth,
        MercadoPagoProperties mercadoPago
) {
    public record OAuthProperties(
            long stateTtlMinutes
    ) {
    }

    public record MercadoPagoProperties(
            boolean mockEnabled,
            String clientId,
            String clientSecret,
            String accessToken,
            String webhookSecret,
            String webhookUrl,
            String successUrl,
            String pendingUrl,
            String failureUrl,
            String apiBaseUrl,
            String authBaseUrl
    ) {
    }
}
