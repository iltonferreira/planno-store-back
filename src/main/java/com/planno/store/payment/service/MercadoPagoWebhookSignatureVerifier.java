package com.planno.store.payment.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.config.PaymentProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MercadoPagoWebhookSignatureVerifier {

    private final PaymentProperties paymentProperties;

    public MercadoPagoWebhookSignatureVerifier(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
    }

    public void verify(String xSignature, String xRequestId, String dataId) {
        if (paymentProperties.mercadoPago().mockEnabled()) {
            if (!StringUtils.hasText(dataId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Mercado Pago webhook data id is required");
            }
            return;
        }
        String secret = paymentProperties.mercadoPago().webhookSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Mercado Pago webhook secret is not configured");
        }
        if (!StringUtils.hasText(xSignature) || !StringUtils.hasText(xRequestId) || !StringUtils.hasText(dataId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid Mercado Pago webhook signature");
        }

        String timestamp = null;
        String receivedHash = null;
        for (String part : xSignature.split(",")) {
            String[] tokens = part.split("=", 2);
            if (tokens.length != 2) {
                continue;
            }
            if ("ts".equals(tokens[0].trim())) {
                timestamp = tokens[1].trim();
            } else if ("v1".equals(tokens[0].trim())) {
                receivedHash = tokens[1].trim();
            }
        }

        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(receivedHash)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid Mercado Pago webhook signature");
        }

        String manifest = "id:" + dataId.toLowerCase(java.util.Locale.ROOT) + ";request-id:" + xRequestId + ";ts:" + timestamp + ";";
        String computedHash = hmacSha256(secret, manifest);
        if (!MessageDigest.isEqual(receivedHash.getBytes(StandardCharsets.UTF_8), computedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid Mercado Pago webhook signature");
        }
    }

    private static String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate webhook signature", exception);
        }
    }
}
