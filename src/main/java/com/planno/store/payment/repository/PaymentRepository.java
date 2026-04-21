package com.planno.store.payment.repository;

import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"store", "order"})
    Optional<Payment> findByIdAndStoreId(UUID id, UUID storeId);

    @EntityGraph(attributePaths = {"store", "order"})
    List<Payment> findAllByOrderIdAndStoreIdOrderByCreatedAtDesc(UUID orderId, UUID storeId);

    @EntityGraph(attributePaths = {"store", "order"})
    Optional<Payment> findFirstByOrderIdAndStoreIdAndStatusInOrderByCreatedAtDesc(
            UUID orderId,
            UUID storeId,
            Collection<PaymentStatus> statuses
    );

    @EntityGraph(attributePaths = {"store", "order"})
    Optional<Payment> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);

    @EntityGraph(attributePaths = {"store", "order"})
    Optional<Payment> findByProviderAndProviderCheckoutId(PaymentProvider provider, String providerCheckoutId);

    @EntityGraph(attributePaths = {"store", "order"})
    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @EntityGraph(attributePaths = {"store", "order"})
    @Query("""
            select p
            from Payment p
            where (:storeId is null or p.store.id = :storeId)
            and (:status is null or p.status = :status)
            order by p.createdAt desc
            """)
    List<Payment> searchPlatform(@Param("storeId") UUID storeId, @Param("status") PaymentStatus status);
}
