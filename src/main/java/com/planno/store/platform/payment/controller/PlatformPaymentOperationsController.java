package com.planno.store.platform.payment.controller;

import com.planno.store.core.security.CurrentActorProvider;
import com.planno.store.payment.dto.StorePaymentResponse;
import com.planno.store.payment.reconciliation.dto.ReconciliationResultResponse;
import com.planno.store.payment.reconciliation.service.PaymentReconciliationService;
import com.planno.store.payment.refund.dto.RefundResponse;
import com.planno.store.payment.service.WebhookService;
import com.planno.store.payment.webhook.dto.WebhookEventResponse;
import com.planno.store.platform.payment.service.PlatformPaymentOperationsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformPaymentOperationsController {

    private final PlatformPaymentOperationsService platformPaymentOperationsService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final WebhookService webhookService;
    private final CurrentActorProvider currentActorProvider;

    public PlatformPaymentOperationsController(
            PlatformPaymentOperationsService platformPaymentOperationsService,
            PaymentReconciliationService paymentReconciliationService,
            WebhookService webhookService,
            CurrentActorProvider currentActorProvider
    ) {
        this.platformPaymentOperationsService = platformPaymentOperationsService;
        this.paymentReconciliationService = paymentReconciliationService;
        this.webhookService = webhookService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/payments")
    public ResponseEntity<List<StorePaymentResponse>> listPayments(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(platformPaymentOperationsService.listPayments(storeId, status));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<StorePaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(platformPaymentOperationsService.getPayment(paymentId));
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    public ResponseEntity<ReconciliationResultResponse> reconcilePayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentReconciliationService.reconcilePayment(paymentId));
    }

    @GetMapping("/refunds")
    public ResponseEntity<List<RefundResponse>> listRefunds(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(platformPaymentOperationsService.listRefunds(storeId, status));
    }

    @GetMapping("/refunds/{refundId}")
    public ResponseEntity<RefundResponse> getRefund(@PathVariable UUID refundId) {
        return ResponseEntity.ok(platformPaymentOperationsService.getRefund(refundId));
    }

    @PostMapping("/refunds/{refundId}/reconcile")
    public ResponseEntity<ReconciliationResultResponse> reconcileRefund(@PathVariable UUID refundId) {
        return ResponseEntity.ok(paymentReconciliationService.reconcileRefund(refundId));
    }

    @GetMapping("/webhooks/events")
    public ResponseEntity<List<WebhookEventResponse>> listWebhookEvents(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean processed,
            @RequestParam(required = false) String eventType
    ) {
        return ResponseEntity.ok(platformPaymentOperationsService.listWebhookEvents(storeId, provider, processed, eventType));
    }

    @GetMapping("/webhooks/events/{id}")
    public ResponseEntity<WebhookEventResponse> getWebhookEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(platformPaymentOperationsService.getWebhookEvent(id));
    }

    @PostMapping("/webhooks/events/{id}/reprocess")
    public ResponseEntity<ReconciliationResultResponse> reprocessWebhookEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.reprocessStoredEvent(
                platformPaymentOperationsService.getWebhookEventEntity(id),
                currentActorProvider.currentActor()
        ));
    }
}
