package com.planno.store.payment.webhook.service;

import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.WebhookEvent;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.store.domain.Store;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventLifecycleService {

    private final WebhookEventRepository webhookEventRepository;

    public WebhookEventLifecycleService(WebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID ensureEvent(PaymentProvider provider, String externalEventId, String eventType, String payload) {
        return webhookEventRepository.findByProviderAndExternalEventId(provider, externalEventId)
                .map(WebhookEvent::getId)
                .orElseGet(() -> saveOrLoadExisting(provider, externalEventId, eventType, payload));
    }

    private UUID saveOrLoadExisting(PaymentProvider provider, String externalEventId, String eventType, String payload) {
        try {
            return webhookEventRepository.saveAndFlush(WebhookEvent.create(null, provider, externalEventId, eventType, payload)).getId();
        } catch (DataIntegrityViolationException exception) {
            return webhookEventRepository.findByProviderAndExternalEventId(provider, externalEventId)
                    .map(WebhookEvent::getId)
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId, Store store, UUID paymentId, UUID refundId, UUID orderId, String processingResult) {
        WebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        event.attachStore(store);
        event.attachReferences(paymentId, refundId, orderId);
        event.markProcessed(processingResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, Store store, UUID paymentId, UUID refundId, UUID orderId, String processingResult) {
        WebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        event.attachStore(store);
        event.attachReferences(paymentId, refundId, orderId);
        event.markFailed(processingResult);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReprocessed(UUID eventId, String actor, String result) {
        WebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        event.markReprocessed(actor, result);
    }
}
