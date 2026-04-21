package com.planno.store.payment.controller;

import com.planno.store.payment.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/webhooks/mercado-pago")
public class PublicMercadoPagoWebhookController {

    private final WebhookService webhookService;

    public PublicMercadoPagoWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam("data.id") String dataId
    ) {
        webhookService.processMercadoPagoWebhook(payload, xSignature, xRequestId, dataId);
        return ResponseEntity.ok().build();
    }
}
