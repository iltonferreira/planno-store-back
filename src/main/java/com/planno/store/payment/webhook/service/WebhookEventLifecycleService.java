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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookEventLifecycleService {

    private final WebhookEventRepository webhookEventRepository;
    private final TransactionTemplate registrationTransaction;
    private final TransactionTemplate recoveryTransaction;

    public WebhookEventLifecycleService(WebhookEventRepository webhookEventRepository,
            PlatformTransactionManager transactionManager) {
        this.webhookEventRepository = webhookEventRepository;
        this.registrationTransaction = new TransactionTemplate(transactionManager);
        this.registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recoveryTransaction = new TransactionTemplate(transactionManager);
        this.recoveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recoveryTransaction.setReadOnly(true);
    }

    public UUID ensureEvent(PaymentProvider provider, String externalEventId, String eventType, String payload) {
        try {
            return registrationTransaction.execute(status -> webhookEventRepository
                    .findByProviderAndExternalEventId(provider, externalEventId)
                    .map(WebhookEvent::getId)
                    .orElseGet(() -> webhookEventRepository.saveAndFlush(
                            WebhookEvent.create(null, provider, externalEventId, eventType, payload)).getId()));
        } catch (DataIntegrityViolationException exception) {
            // PostgreSQL aborts the losing INSERT transaction. Query only after its rollback.
            return recoveryTransaction.execute(status -> webhookEventRepository
                    .findByProviderAndExternalEventId(provider, externalEventId)
                    .map(WebhookEvent::getId)
                    .orElseThrow(() -> exception));
        }
    }

    @Transactional
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

    @Transactional
    public void markReprocessed(UUID eventId, String actor, String result) {
        WebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        event.markReprocessed(actor, result);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedReprocess(UUID eventId, String actor) {
        WebhookEvent event = webhookEventRepository.findById(eventId).orElseThrow();
        event.markFailed("Provider event reprocessing failed");
        event.markReprocessed(actor, "Reprocess failed");
    }
}
