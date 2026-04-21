package com.planno.store.payment.webhook.controller;

import com.planno.store.core.security.CurrentActorProvider;
import com.planno.store.payment.reconciliation.dto.ReconciliationResultResponse;
import com.planno.store.payment.service.WebhookService;
import com.planno.store.payment.webhook.dto.WebhookEventResponse;
import com.planno.store.payment.webhook.service.WebhookEventAdminService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/webhooks/events")
public class AdminWebhookEventController {

    private final WebhookEventAdminService webhookEventAdminService;
    private final WebhookService webhookService;
    private final CurrentActorProvider currentActorProvider;

    public AdminWebhookEventController(
            WebhookEventAdminService webhookEventAdminService,
            WebhookService webhookService,
            CurrentActorProvider currentActorProvider
    ) {
        this.webhookEventAdminService = webhookEventAdminService;
        this.webhookService = webhookService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<WebhookEventResponse>> list(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean processed,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(webhookEventAdminService.list(provider, processed, eventType, from, to));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<WebhookEventResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookEventAdminService.get(id));
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PLATFORM_ADMIN')")
    public ResponseEntity<ReconciliationResultResponse> reprocess(@PathVariable UUID id) {
        var event = webhookEventAdminService.getEntity(id);
        return ResponseEntity.ok(webhookService.reprocessStoredEvent(event, currentActorProvider.currentActor()));
    }
}
