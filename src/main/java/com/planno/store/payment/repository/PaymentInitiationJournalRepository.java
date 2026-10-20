package com.planno.store.payment.repository;

import com.planno.store.payment.domain.PaymentInitiationJournal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentInitiationJournalRepository extends JpaRepository<PaymentInitiationJournal, UUID> {
    boolean existsByStoreIdAndOrderIdAndResolvedFalse(UUID storeId, UUID orderId);
    Optional<PaymentInitiationJournal> findByIdAndStoreIdAndOrderId(UUID id, UUID storeId, UUID orderId);
}
