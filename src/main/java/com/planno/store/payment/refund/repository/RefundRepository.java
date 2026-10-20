package com.planno.store.payment.refund.repository;

import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    List<Refund> findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(UUID paymentId, UUID storeId);

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    List<Refund> findAllByOrderIdAndStoreIdOrderByCreatedAtDesc(UUID orderId, UUID storeId);

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    Optional<Refund> findByProviderAndProviderRefundId(PaymentProvider provider, String providerRefundId);

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    Optional<Refund> findByProviderAndProviderRefundIdAndPaymentIdAndStoreIdAndProviderStateSummaryFalse(
            PaymentProvider provider, String providerRefundId, UUID paymentId, UUID storeId);

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    List<Refund> findAllByPaymentIdAndStoreIdAndProviderStateSummaryTrue(UUID paymentId, UUID storeId);

    boolean existsByPaymentIdAndStoreIdAndInventoryRestoredTrue(UUID paymentId, UUID storeId);

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    Optional<Refund> findTopByPaymentIdAndStoreIdAndStatusInOrderByCreatedAtDesc(UUID paymentId, UUID storeId, Collection<RefundStatus> statuses);

    @Query("""
            select coalesce(sum(r.amount), 0)
            from Refund r
            where r.payment.id = :paymentId
            and r.store.id = :storeId
            and r.status in :statuses
            """)
    BigDecimal sumAmountsByPaymentAndStoreAndStatuses(
            @Param("paymentId") UUID paymentId,
            @Param("storeId") UUID storeId,
            @Param("statuses") Collection<RefundStatus> statuses
    );

    @EntityGraph(attributePaths = {"store", "payment", "order"})
    @Query("""
            select r
            from Refund r
            where (:storeId is null or r.store.id = :storeId)
            and (:status is null or r.status = :status)
            order by r.createdAt desc
            """)
    List<Refund> searchPlatform(@Param("storeId") UUID storeId, @Param("status") RefundStatus status);
}
