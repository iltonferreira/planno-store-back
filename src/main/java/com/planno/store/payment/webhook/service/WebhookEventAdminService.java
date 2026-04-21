package com.planno.store.payment.webhook.service;

import com.planno.store.core.exception.NotFoundException;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.WebhookEvent;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.payment.webhook.dto.WebhookEventResponse;
import com.planno.store.store.service.CurrentStoreProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEventAdminService {

    private final WebhookEventRepository webhookEventRepository;
    private final CurrentStoreProvider currentStoreProvider;

    public WebhookEventAdminService(WebhookEventRepository webhookEventRepository, CurrentStoreProvider currentStoreProvider) {
        this.webhookEventRepository = webhookEventRepository;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> list(String provider, Boolean processed, String eventType, OffsetDateTime from, OffsetDateTime to) {
        PaymentProvider providerEnum = provider == null || provider.isBlank() ? null : PaymentProvider.valueOf(provider.trim().toUpperCase());
        return webhookEventRepository.searchByStore(currentStoreProvider.getCurrentStoreId(), providerEnum, processed, eventType, from, to)
                .stream()
                .map(WebhookEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEventResponse get(UUID id) {
        return WebhookEventResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public WebhookEvent getEntity(UUID id) {
        return webhookEventRepository.findByIdAndStoreId(id, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Webhook event not found"));
    }
}
