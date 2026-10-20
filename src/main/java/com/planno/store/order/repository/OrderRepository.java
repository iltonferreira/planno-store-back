package com.planno.store.order.repository;

import com.planno.store.order.domain.Order;
import com.planno.store.payment.domain.PaymentStatus;
import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    interface ReservationCandidate {
        UUID getId();
        UUID getStoreId();
    }

    @Query("""
            select o.id as id, o.store.id as storeId from Order o
            where o.status = com.planno.store.order.domain.OrderStatus.WAITING_PAYMENT
              and o.reservationExpiresAt is not null and o.reservationExpiresAt <= :cutoff
              and (:afterId is null or o.id > :afterId)
              and not exists (select p.id from Payment p where p.order.id = o.id
                and (p.store.id <> o.store.id or p.status is null or p.status not in :terminalStatuses))
            order by o.id
            """)
    List<ReservationCandidate> findReservationExpirationCandidates(
            @Param("cutoff") OffsetDateTime cutoff, @Param("afterId") UUID afterId,
            @Param("terminalStatuses") Collection<PaymentStatus> terminalStatuses, Pageable pageable);

    @Query("""
            select count(p) from Payment p where p.order.id = :orderId
            and (p.store.id <> :storeId or p.status is null or p.status not in :terminalStatuses)
            """)
    long countReservationProtectingPayments(@Param("orderId") UUID orderId,
            @Param("storeId") UUID storeId, @Param("terminalStatuses") Collection<PaymentStatus> terminalStatuses);

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    List<Order> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    List<Order> findAllByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID storeId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    List<Order> findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    Optional<Order> findByIdAndStoreId(UUID id, UUID storeId);

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    Optional<Order> findByIdAndStoreIdAndCartId(UUID id, UUID storeId, UUID cartId);

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    Optional<Order> findByStoreIdAndCartId(UUID storeId, UUID cartId);
}
