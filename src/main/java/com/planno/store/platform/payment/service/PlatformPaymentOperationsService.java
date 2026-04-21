package com.planno.store.platform.payment.service;

import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.WebhookEvent;
import com.planno.store.payment.dto.StorePaymentResponse;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import com.planno.store.payment.refund.dto.RefundResponse;
import com.planno.store.payment.refund.repository.RefundRepository;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.payment.webhook.dto.WebhookEventResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformPaymentOperationsService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WebhookEventRepository webhookEventRepository;

    public PlatformPaymentOperationsService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            WebhookEventRepository webhookEventRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional(readOnly = true)
    public List<StorePaymentResponse> listPayments(UUID storeId, String status) {
        PaymentStatus paymentStatus = parsePaymentStatus(status);
        return paymentRepository.searchPlatform(storeId, paymentStatus).stream().map(StorePaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StorePaymentResponse getPayment(UUID paymentId) {
        return StorePaymentResponse.from(getPaymentEntity(paymentId));
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listRefunds(UUID storeId, String status) {
        RefundStatus refundStatus = parseRefundStatus(status);
        return refundRepository.searchPlatform(storeId, refundStatus).stream().map(RefundResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(UUID refundId) {
        return RefundResponse.from(getRefundEntity(refundId));
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> listWebhookEvents(UUID storeId, String provider, Boolean processed, String eventType) {
        PaymentProvider paymentProvider = parseProvider(provider);
        return webhookEventRepository.searchPlatform(storeId, paymentProvider, processed, eventType).stream().map(WebhookEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WebhookEventResponse getWebhookEvent(UUID id) {
        return WebhookEventResponse.from(getWebhookEventEntity(id));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentEntity(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public Refund getRefundEntity(UUID refundId) {
        return refundRepository.findById(refundId).orElseThrow(() -> new NotFoundException("Refund not found"));
    }

    @Transactional(readOnly = true)
    public WebhookEvent getWebhookEventEntity(UUID id) {
        return webhookEventRepository.findById(id).orElseThrow(() -> new NotFoundException("Webhook event not found"));
    }

    private PaymentStatus parsePaymentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid payment status filter");
        }
    }

    private RefundStatus parseRefundStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RefundStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid refund status filter");
        }
    }

    private PaymentProvider parseProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        try {
            return PaymentProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid payment provider filter");
        }
    }
}
