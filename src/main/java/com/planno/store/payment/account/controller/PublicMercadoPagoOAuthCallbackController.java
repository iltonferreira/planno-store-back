package com.planno.store.payment.account.controller;

import com.planno.store.payment.account.dto.OAuthCallbackResponse;
import com.planno.store.payment.account.service.MercadoPagoOAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/payments/mercado-pago/oauth")
public class PublicMercadoPagoOAuthCallbackController {

    private final MercadoPagoOAuthService mercadoPagoOAuthService;

    public PublicMercadoPagoOAuthCallbackController(MercadoPagoOAuthService mercadoPagoOAuthService) {
        this.mercadoPagoOAuthService = mercadoPagoOAuthService;
    }

    @GetMapping("/callback")
    public ResponseEntity<OAuthCallbackResponse> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        return ResponseEntity.ok(mercadoPagoOAuthService.handleCallback(code, state, error));
    }
}
