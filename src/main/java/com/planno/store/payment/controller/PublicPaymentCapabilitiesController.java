package com.planno.store.payment.controller;

import com.planno.store.payment.account.dto.PublicTransparentCheckoutCapabilitiesResponse;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/payments/transparent")
public class PublicPaymentCapabilitiesController {

    private final PaymentProviderAccountService paymentProviderAccountService;

    public PublicPaymentCapabilitiesController(PaymentProviderAccountService paymentProviderAccountService) {
        this.paymentProviderAccountService = paymentProviderAccountService;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<PublicTransparentCheckoutCapabilitiesResponse> capabilities() {
        return ResponseEntity.ok(paymentProviderAccountService.getPublicTransparentCheckoutCapabilities());
    }
}
