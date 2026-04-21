package com.planno.store.payment.controller;

import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.dto.CreateTransparentPaymentRequest;
import com.planno.store.payment.dto.PaymentInitiationResponse;
import com.planno.store.payment.dto.TransparentPaymentResponse;
import com.planno.store.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/payments")
public class PublicPaymentController {

    private final PaymentService paymentService;

    public PublicPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentInitiationResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiate(request));
    }

    @PostMapping("/transparent")
    public ResponseEntity<TransparentPaymentResponse> createTransparent(@Valid @RequestBody CreateTransparentPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.initiateTransparent(request));
    }
}
