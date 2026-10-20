package com.planno.store.payment.service;

import com.planno.store.payment.domain.PaymentInitiationJournal;
import com.planno.store.payment.repository.PaymentInitiationJournalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentInitiationJournalService {
    private final PaymentInitiationJournalRepository repository;

    public PaymentInitiationJournalService(PaymentInitiationJournalRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void register(UUID storeId, UUID orderId, UUID attemptId) {
        repository.saveAndFlush(PaymentInitiationJournal.started(storeId, orderId, attemptId));
    }

    // The result becomes known only when the corresponding Payment transaction commits.
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolve(UUID storeId, UUID orderId, UUID attemptId) {
        repository.findByIdAndStoreIdAndOrderId(attemptId, storeId, orderId)
                .orElseThrow(() -> new IllegalStateException("Payment initiation journal is missing"))
                .resolve();
    }

    @Transactional(readOnly = true)
    public boolean hasUnresolvedInitiation(UUID storeId, UUID orderId) {
        return repository.existsByStoreIdAndOrderIdAndResolvedFalse(storeId, orderId);
    }
}
