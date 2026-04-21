package com.planno.store.payment.webhook.dto;

import com.planno.store.payment.domain.WebhookEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookEventResponse(
        UUID id,
        UUID storeId,
        String provider,
        String externalEventId,
        String eventType,
        boolean processed,
        OffsetDateTime processedAt,
        String processingResult,
        UUID paymentId,
        UUID refundId,
        UUID orderId,
        int reprocessCount,
        OffsetDateTime lastReprocessedAt,
        String lastReprocessedBy,
        String lastReprocessResult,
        String payloadPreview,
        OffsetDateTime createdAt
) {
    public static WebhookEventResponse from(WebhookEvent event) {
        String payload = event.getPayload();
        String payloadPreview = payload == null ? null : payload.substring(0, Math.min(payload.length(), 500));
        return new WebhookEventResponse(
                event.getId(),
                event.getStore() != null ? event.getStore().getId() : null,
                event.getProvider().name(),
                event.getExternalEventId(),
                event.getEventType(),
                event.isProcessed(),
                event.getProcessedAt(),
                event.getProcessingResult(),
                event.getPaymentId(),
                event.getRefundId(),
                event.getOrderId(),
                event.getReprocessCount(),
                event.getLastReprocessedAt(),
                event.getLastReprocessedBy(),
                event.getLastReprocessResult(),
                payloadPreview,
                event.getCreatedAt()
        );
    }
}
