package com.planno.store.payment.account.repository;

import com.planno.store.payment.account.domain.PaymentProviderOAuthState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderOAuthStateRepository extends JpaRepository<PaymentProviderOAuthState, UUID> {

    Optional<PaymentProviderOAuthState> findByState(String state);
}
