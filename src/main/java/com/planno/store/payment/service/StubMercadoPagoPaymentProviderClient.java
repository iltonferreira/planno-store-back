package com.planno.store.payment.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.payment.mercado-pago", name = "mock-enabled", havingValue = "true")
public class StubMercadoPagoPaymentProviderClient implements PaymentProviderClient {

    private final Map<String, StubPaymentState> payments = new ConcurrentHashMap<>();
    private final Map<String, StubRefundState> refunds = new ConcurrentHashMap<>();

    @Override
    public PaymentCheckoutResult createCheckout(Payment payment) {
        String checkoutId = "pref-" + UUID.randomUUID();
        String providerPaymentId = "pay-" + UUID.randomUUID();
        payments.put(checkoutId, new StubPaymentState(
                providerPaymentId,
                checkoutId,
                payment.getProviderMerchantId(),
                PaymentStatus.PENDING,
                "pending_waiting_payment",
                payment.getAmount(),
                payment.getCurrency(),
                "mercado_pago",
                payment.getOrder().getCode(),
                payment.getId().toString()
        ));
        return new PaymentCheckoutResult(
                null,
                checkoutId,
                "https://sandbox.mercadopago.test/checkout/" + checkoutId,
                PaymentStatus.PENDING,
                null,
                null,
                "{\"id\":\"" + checkoutId + "\",\"init_point\":\"https://sandbox.mercadopago.test/checkout/" + checkoutId + "\"}"
        );
    }

    @Override
    public PaymentCheckoutResult createTransparentPayment(Payment payment, TransparentPaymentProviderRequest request) {
        if ("tok_error".equals(request.token())) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Mock transparent payment provider error");
        }

        String providerPaymentId = "pay-" + UUID.randomUUID();
        StubPaymentState state = new StubPaymentState(
                providerPaymentId,
                null,
                payment.getProviderMerchantId(),
                PaymentStatus.PENDING,
                "pending_waiting_payment",
                payment.getAmount(),
                payment.getCurrency(),
                request.paymentMethodId(),
                payment.getOrder().getCode(),
                payment.getId().toString()
        );
        payments.put(providerPaymentId, state);
        return new PaymentCheckoutResult(
                providerPaymentId,
                null,
                null,
                state.status(),
                state.statusDetail(),
                state.paymentMethod(),
                "{\"id\":\"" + providerPaymentId + "\",\"status\":\"pending\",\"status_detail\":\"pending_waiting_payment\"}"
        );
    }

    @Override
    public PaymentProviderPaymentDetails getPaymentDetails(PaymentProviderAccount account, String externalResourceId) {
        StubPaymentState state = payments.get(externalResourceId);
        if (state == null) {
            state = payments.values().stream()
                    .filter(candidate -> candidate.providerPaymentId().equals(externalResourceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Mock payment not found: " + externalResourceId));
        }
        return new PaymentProviderPaymentDetails(
                state.providerPaymentId(),
                state.providerCheckoutId(),
                state.providerMerchantId(),
                state.status(),
                state.statusDetail(),
                state.amount(),
                state.currency(),
                state.paymentMethod(),
                state.internalPaymentId()
        );
    }

    @Override
    public PaymentProviderRefundDetails createRefund(Refund refund) {
        String refundId = "refund-" + UUID.randomUUID();
        RefundStatus refundStatus = RefundStatus.APPROVED;
        StubPaymentState paymentState = payments.values().stream()
                .filter(candidate -> candidate.internalPaymentId().equals(refund.getPayment().getId().toString()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mock payment not found for refund: " + refund.getPayment().getId()));

        PaymentStatus newPaymentStatus = refund.getAmount().compareTo(refund.getPayment().getAmount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;

        payments.put(paymentState.providerCheckoutId() != null ? paymentState.providerCheckoutId() : paymentState.providerPaymentId(), new StubPaymentState(
                paymentState.providerPaymentId(),
                paymentState.providerCheckoutId(),
                paymentState.providerMerchantId(),
                newPaymentStatus,
                defaultStatusDetail(newPaymentStatus),
                paymentState.amount(),
                paymentState.currency(),
                paymentState.paymentMethod(),
                paymentState.externalReference(),
                paymentState.internalPaymentId()
        ));

        StubRefundState refundState = new StubRefundState(
                refundId,
                paymentState.providerPaymentId(),
                refundStatus,
                refund.getAmount().setScale(2, RoundingMode.HALF_UP),
                refund.getCurrency()
        );
        refunds.put(refundId, refundState);
        return new PaymentProviderRefundDetails(
                refundId,
                paymentState.providerPaymentId(),
                refundStatus,
                refundState.amount(),
                refundState.currency(),
                "{\"id\":\"" + refundId + "\",\"status\":\"approved\"}"
        );
    }

    @Override
    public PaymentProviderRefundDetails getRefundDetails(PaymentProviderAccount account, String externalRefundId) {
        StubRefundState state = refunds.get(externalRefundId);
        if (state == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Mock refund not found: " + externalRefundId);
        }
        return new PaymentProviderRefundDetails(
                state.providerRefundId(),
                state.providerPaymentId(),
                state.status(),
                state.amount(),
                state.currency(),
                "{\"id\":\"" + state.providerRefundId() + "\",\"status\":\"" + state.status().name().toLowerCase() + "\"}"
        );
    }

    public void simulatePaymentStatus(String checkoutId, PaymentStatus status, String paymentMethod) {
        StubPaymentState current = payments.get(checkoutId);
        String resourceKey = checkoutId;
        if (current == null) {
            current = payments.values().stream()
                    .filter(candidate -> checkoutId.equals(candidate.providerPaymentId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Mock payment not found: " + checkoutId));
            resourceKey = current.providerCheckoutId() != null ? current.providerCheckoutId() : current.providerPaymentId();
        }
        payments.put(resourceKey, new StubPaymentState(
                current.providerPaymentId(),
                current.providerCheckoutId(),
                current.providerMerchantId(),
                status,
                defaultStatusDetail(status),
                current.amount().setScale(2, RoundingMode.HALF_UP),
                current.currency(),
                paymentMethod,
                current.externalReference(),
                current.internalPaymentId()
        ));
    }

    private record StubPaymentState(
            String providerPaymentId,
            String providerCheckoutId,
            String providerMerchantId,
            PaymentStatus status,
            String statusDetail,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String externalReference,
            String internalPaymentId
    ) {
    }

    private record StubRefundState(
            String providerRefundId,
            String providerPaymentId,
            RefundStatus status,
            BigDecimal amount,
            String currency
    ) {
    }

    private static String defaultStatusDetail(PaymentStatus status) {
        return switch (status) {
            case APPROVED -> "accredited";
            case REJECTED -> "cc_rejected_other_reason";
            case CANCELLED -> "cancelled";
            case REFUNDED -> "refunded";
            case PARTIALLY_REFUNDED -> "partially_refunded";
            default -> "pending_waiting_payment";
        };
    }
}
