package com.planno.store.payment.account.controller;

import com.planno.store.payment.account.dto.OAuthConnectionResponse;
import com.planno.store.payment.account.dto.PaymentProviderAccountStatusResponse;
import com.planno.store.payment.account.service.MercadoPagoOAuthService;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments/mercado-pago")
public class AdminMercadoPagoAccountController {

    private final MercadoPagoOAuthService mercadoPagoOAuthService;
    private final PaymentProviderAccountService paymentProviderAccountService;

    public AdminMercadoPagoAccountController(
            MercadoPagoOAuthService mercadoPagoOAuthService,
            PaymentProviderAccountService paymentProviderAccountService
    ) {
        this.mercadoPagoOAuthService = mercadoPagoOAuthService;
        this.paymentProviderAccountService = paymentProviderAccountService;
    }

    @GetMapping("/connect")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<OAuthConnectionResponse> connect() {
        return ResponseEntity.ok(mercadoPagoOAuthService.startConnection());
    }

    @GetMapping("/account")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<PaymentProviderAccountStatusResponse> account() {
        return ResponseEntity.ok(paymentProviderAccountService.getCurrentAccountStatus());
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<PaymentProviderAccountStatusResponse> capabilities() {
        return ResponseEntity.ok(paymentProviderAccountService.getCurrentAccountStatus());
    }

    @PostMapping("/disconnect")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<PaymentProviderAccountStatusResponse> disconnect() {
        return ResponseEntity.ok(paymentProviderAccountService.disconnectCurrentStoreAccount());
    }
}
