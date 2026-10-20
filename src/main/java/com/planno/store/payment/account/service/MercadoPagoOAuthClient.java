package com.planno.store.payment.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.config.PaymentProperties;
import java.time.OffsetDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class MercadoPagoOAuthClient {

    private final RestClient restClient;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public MercadoPagoOAuthClient(
            RestClient.Builder restClientBuilder,
            PaymentProperties paymentProperties,
            ObjectMapper objectMapper
    ) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(paymentProperties.mercadoPago().apiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    public MercadoPagoOAuthTokenResponse exchangeCode(String code, String redirectUri) {
        if (paymentProperties.mercadoPago().mockEnabled()) {
            return new MercadoPagoOAuthTokenResponse(
                    "mock-access-token-" + code,
                    "mock-refresh-token-" + code,
                    "read write offline_access",
                    "mock-user-" + Math.abs(code.hashCode()),
                    "mock-public-key",
                    OffsetDateTime.now().plusDays(180)
            );
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", paymentProperties.mercadoPago().clientId());
            form.add("client_secret", paymentProperties.mercadoPago().clientSecret());
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
            form.add("test_token", Boolean.toString(paymentProperties.mercadoPago().testToken()));

            String rawResponse = restClient.post()
                    .uri("/oauth/token")
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(rawResponse);
            String accessToken = node.path("access_token").asText(null);
            validateTokenMode(node, accessToken);
            String refreshToken = node.path("refresh_token").asText(null);
            String userId = node.path("user_id").asText(null);
            long expiresIn = node.path("expires_in").asLong(0L);
            if (!hasText(refreshToken) || !hasText(userId) || expiresIn <= 0) {
                throw new IllegalArgumentException("OAuth response is missing required token fields");
            }
            return new MercadoPagoOAuthTokenResponse(
                    accessToken,
                    refreshToken,
                    node.path("scope").asText(null),
                    userId,
                    node.path("public_key").asText(null),
                    OffsetDateTime.now().plusSeconds(expiresIn)
            );
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to complete Mercado Pago OAuth exchange");
        }
    }

    private void validateTokenMode(JsonNode response, String accessToken) {
        boolean testMode = paymentProperties.mercadoPago().testToken();
        if (!hasText(accessToken)) {
            throw new IllegalArgumentException("OAuth response is missing an access token");
        }

        JsonNode liveMode = response.get("live_mode");
        if (testMode) {
            if (!accessToken.startsWith("TEST-")
                    || (liveMode != null && (!liveMode.isBoolean() || liveMode.booleanValue()))) {
                throw new IllegalArgumentException("OAuth response does not match configured sandbox mode");
            }
        } else if (!accessToken.startsWith("APP_USR-")
                || liveMode == null || !liveMode.isBoolean() || !liveMode.booleanValue()) {
            throw new IllegalArgumentException("OAuth response does not match configured production mode");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
