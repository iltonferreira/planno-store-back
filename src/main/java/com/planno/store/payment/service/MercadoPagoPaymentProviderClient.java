package com.planno.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "app.payment.mercado-pago", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
public class MercadoPagoPaymentProviderClient implements PaymentProviderClient {

    private final RestClient restClient;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final PaymentProviderAccountService paymentProviderAccountService;

    public MercadoPagoPaymentProviderClient(
            RestClient.Builder restClientBuilder,
            PaymentProperties paymentProperties,
            ObjectMapper objectMapper,
            PaymentProviderAccountService paymentProviderAccountService
    ) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.paymentProviderAccountService = paymentProviderAccountService;
        this.restClient = restClientBuilder
                .baseUrl(paymentProperties.mercadoPago().apiBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PaymentCheckoutResult createCheckout(Payment payment) {
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForCurrentStore();
        String sellerAccessToken = paymentProviderAccountService.decryptAccessToken(account);
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", payment.getOrder().getItems().stream().map(item -> Map.of(
                "id", item.getProduct().getId().toString(),
                "title", item.getProductNameSnapshot(),
                "quantity", item.getQuantity(),
                "currency_id", payment.getCurrency(),
                "unit_price", item.getUnitPriceSnapshot().setScale(2, RoundingMode.HALF_UP)
        )).toList());
        payload.put("external_reference", payment.getOrder().getCode());
        payload.put("notification_url", paymentProperties.mercadoPago().webhookUrl());
        payload.put("metadata", Map.of(
                "internal_payment_id", payment.getId().toString(),
                "order_id", payment.getOrder().getId().toString(),
                "store_id", payment.getStore().getId().toString(),
                "provider_user_id", payment.getProviderMerchantId()
        ));
        payload.put("marketplace_fee", payment.getCommissionAmount().setScale(2, RoundingMode.HALF_UP));

        Map<String, String> backUrls = new HashMap<>();
        String storeDomain = payment.getStore().getPublicDomain();
        if (StringUtils.hasText(storeDomain)) {
            String returnUrl = "https://" + storeDomain + "/checkout/retorno";
            backUrls.put("success", returnUrl + "?status=success");
            backUrls.put("pending", returnUrl + "?status=pending");
            backUrls.put("failure", returnUrl + "?status=failure");
        } else {
            putIfPresent(backUrls, "success", paymentProperties.mercadoPago().successUrl());
            putIfPresent(backUrls, "pending", paymentProperties.mercadoPago().pendingUrl());
            putIfPresent(backUrls, "failure", paymentProperties.mercadoPago().failureUrl());
        }
        if (!backUrls.isEmpty()) {
            payload.put("back_urls", backUrls);
            payload.put("auto_return", "approved");
        }

        try {
            String rawResponse = restClient.post()
                    .uri("/checkout/preferences")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerAccessToken)
                    .header("X-Idempotency-Key", payment.getIdempotencyKey())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(rawResponse);
            return new PaymentCheckoutResult(
                    null,
                    textOrNull(response, "id"),
                    firstNonBlank(textOrNull(response, "init_point"), textOrNull(response, "sandbox_init_point")),
                    PaymentStatus.PENDING,
                    null,
                    null,
                    rawResponse
            );
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to initialize Mercado Pago checkout");
        }
    }

    @Override
    public PaymentCheckoutResult createTransparentPayment(Payment payment, TransparentPaymentProviderRequest request) {
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForStore(payment.getStore().getId());
        String sellerAccessToken = paymentProviderAccountService.decryptAccessToken(account);

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", request.token());
        payload.put("payment_method_id", request.paymentMethodId());
        payload.put("installments", request.installments());
        payload.put("payer", buildPayerPayload(request));
        payload.put("transaction_amount", payment.getAmount().setScale(2, RoundingMode.HALF_UP));
        payload.put("description", "Order " + payment.getOrder().getCode());
        payload.put("external_reference", payment.getId().toString());
        payload.put("notification_url", paymentProperties.mercadoPago().webhookUrl());
        payload.put("application_fee", payment.getCommissionAmount().setScale(2, RoundingMode.HALF_UP));
        payload.put("metadata", Map.of(
                "internal_payment_id", payment.getId().toString(),
                "order_id", payment.getOrder().getId().toString(),
                "store_id", payment.getStore().getId().toString(),
                "provider_user_id", payment.getProviderMerchantId()
        ));
        putIfPresentObject(payload, "issuer_id", request.issuerId());
        if (StringUtils.hasText(request.deviceId())) {
            payload.put("device", Map.of("id", request.deviceId()));
        }

        try {
            String rawResponse = restClient.post()
                    .uri("/v1/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerAccessToken)
                    .header("X-Idempotency-Key", payment.getIdempotencyKey())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(rawResponse);
            return new PaymentCheckoutResult(
                    textOrNull(response, "id"),
                    null,
                    null,
                    mapMercadoPagoStatus(textOrNull(response, "status")),
                    textOrNull(response, "status_detail"),
                    firstNonBlank(textOrNull(response, "payment_method_id"), request.paymentMethodId()),
                    rawResponse
            );
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to initialize Mercado Pago transparent payment");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to parse Mercado Pago transparent payment response");
        }
    }

    @Override
    public PaymentProviderPaymentDetails getPaymentDetails(PaymentProviderAccount account, String externalResourceId) {
        String sellerAccessToken = paymentProviderAccountService.decryptAccessToken(account);
        try {
            String rawResponse = restClient.get()
                    .uri("/v1/payments/{id}", externalResourceId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerAccessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(rawResponse);
            JsonNode metadata = response.path("metadata");
            return new PaymentProviderPaymentDetails(
                    textOrNull(response, "id"),
                    firstNonBlank(textOrNull(metadata, "preference_id"), textOrNull(response.path("point_of_interaction").path("transaction_data"), "qr_code_base64")),
                    firstNonBlank(textOrNull(response, "user_id"), textOrNull(metadata, "provider_user_id")),
                    mapMercadoPagoStatus(textOrNull(response, "status")),
                    textOrNull(response, "status_detail"),
                    decimalOrZero(response, "transaction_amount"),
                    firstNonBlank(textOrNull(response, "currency_id"), "BRL"),
                    textOrNull(response, "payment_method_id"),
                    firstNonBlank(textOrNull(metadata, "internal_payment_id"), textOrNull(response, "external_reference"))
            );
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to query Mercado Pago payment details");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to parse Mercado Pago payment details");
        }
    }

    @Override
    public PaymentProviderRefundDetails createRefund(Refund refund) {
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForStore(refund.getStore().getId());
        String sellerAccessToken = paymentProviderAccountService.decryptAccessToken(account);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", refund.getAmount());
            if (StringUtils.hasText(refund.getReason())) {
                payload.put("reason", refund.getReason());
            }

            String rawResponse = restClient.post()
                    .uri("/v1/payments/{id}/refunds", refund.getPayment().getProviderPaymentId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerAccessToken)
                    .header("X-Idempotency-Key", refund.getId().toString())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(rawResponse);
            return new PaymentProviderRefundDetails(
                    textOrNull(response, "id"),
                    firstNonBlank(textOrNull(response, "payment_id"), refund.getPayment().getProviderPaymentId()),
                    mapMercadoPagoRefundStatus(textOrNull(response, "status")),
                    decimalOrZero(response, "amount"),
                    firstNonBlank(textOrNull(response, "currency_id"), refund.getCurrency()),
                    rawResponse
            );
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to initialize Mercado Pago refund");
        }
    }

    @Override
    public PaymentProviderRefundDetails getRefundDetails(PaymentProviderAccount account, String externalRefundId) {
        String sellerAccessToken = paymentProviderAccountService.decryptAccessToken(account);
        try {
            String rawResponse = restClient.get()
                    .uri("/v1/refunds/{id}", externalRefundId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerAccessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(rawResponse);
            return new PaymentProviderRefundDetails(
                    textOrNull(response, "id"),
                    textOrNull(response, "payment_id"),
                    mapMercadoPagoRefundStatus(textOrNull(response, "status")),
                    decimalOrZero(response, "amount"),
                    firstNonBlank(textOrNull(response, "currency_id"), "BRL"),
                    rawResponse
            );
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to query Mercado Pago refund details");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Failed to parse Mercado Pago refund details");
        }
    }

    private static PaymentStatus mapMercadoPagoStatus(String status) {
        if (status == null) {
            return PaymentStatus.FAILED;
        }
        return switch (status.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "pending", "in_process", "authorized" -> PaymentStatus.PENDING;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            case "refunded", "charged_back" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.FAILED;
        };
    }

    private static RefundStatus mapMercadoPagoRefundStatus(String status) {
        if (status == null) {
            return RefundStatus.FAILED;
        }
        return switch (status.toLowerCase()) {
            case "approved" -> RefundStatus.APPROVED;
            case "pending", "in_process" -> RefundStatus.PENDING;
            case "rejected" -> RefundStatus.REJECTED;
            case "cancelled" -> RefundStatus.CANCELLED;
            default -> RefundStatus.FAILED;
        };
    }

    private static BigDecimal decimalOrZero(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return node.get(field).decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private static void putIfPresentObject(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private static Map<String, Object> buildPayerPayload(TransparentPaymentProviderRequest request) {
        Map<String, Object> payer = new HashMap<>();
        payer.put("email", request.payerEmail());
        putIfPresentObject(payer, "first_name", request.payerFirstName());
        putIfPresentObject(payer, "last_name", request.payerLastName());
        if (StringUtils.hasText(request.identificationType()) && StringUtils.hasText(request.identificationNumber())) {
            payer.put("identification", Map.of(
                    "type", request.identificationType(),
                    "number", request.identificationNumber()
            ));
        }
        return payer;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
