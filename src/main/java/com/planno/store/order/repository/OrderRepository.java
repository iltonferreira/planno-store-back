package com.planno.store.order.repository;

import com.planno.store.order.domain.Order;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

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
}
