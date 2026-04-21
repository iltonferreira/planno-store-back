package com.planno.store.payment.reconciliation.controller;

import com.planno.store.payment.reconciliation.dto.ReconciliationResultResponse;
import com.planno.store.payment.reconciliation.service.PaymentReconciliationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReconciliationController {

    private final PaymentReconciliationService paymentReconciliationService;

    public AdminReconciliationController(PaymentReconciliationService paymentReconciliationService) {
        this.paymentReconciliationService = paymentReconciliationService;
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<ReconciliationResultResponse> reconcilePayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentReconciliationService.reconcilePayment(paymentId));
    }

    @PostMapping("/refunds/{refundId}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<ReconciliationResultResponse> reconcileRefund(@PathVariable UUID refundId) {
        return ResponseEntity.ok(paymentReconciliationService.reconcileRefund(refundId));
    }
}
