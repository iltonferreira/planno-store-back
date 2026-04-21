package com.planno.store.payment.controller;

import com.planno.store.payment.dto.StorePaymentResponse;
import com.planno.store.payment.service.PaymentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<StorePaymentResponse>> listByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.listByOrder(orderId));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<StorePaymentResponse> get(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.get(paymentId));
    }
}
