package com.planno.store.payment.account.repository;

import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.PaymentProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderAccountRepository extends JpaRepository<PaymentProviderAccount, UUID> {

    @EntityGraph(attributePaths = {"store"})
    Optional<PaymentProviderAccount> findByStoreIdAndProvider(UUID storeId, PaymentProvider provider);

    @EntityGraph(attributePaths = {"store"})
    Optional<PaymentProviderAccount> findByProviderAndProviderUserId(PaymentProvider provider, String providerUserId);
}
