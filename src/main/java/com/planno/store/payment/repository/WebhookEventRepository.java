package com.planno.store.payment.repository;

import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.WebhookEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    @EntityGraph(attributePaths = {"store"})
    Optional<WebhookEvent> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId);

    @EntityGraph(attributePaths = {"store"})
    Optional<WebhookEvent> findByIdAndStoreId(UUID id, UUID storeId);

    @EntityGraph(attributePaths = {"store"})
    @Query("""
            select e
            from WebhookEvent e
            where e.store.id = :storeId
            and (:provider is null or e.provider = :provider)
            and (:processed is null or e.processed = :processed)
            and (:eventType is null or upper(e.eventType) = upper(cast(:eventType as string)))
            and (cast(:from as OffsetDateTime) is null or e.createdAt >= :from)
            and (cast(:to as OffsetDateTime) is null or e.createdAt <= :to)
            order by e.createdAt desc
            """)
    List<WebhookEvent> searchByStore(
            @Param("storeId") UUID storeId,
            @Param("provider") PaymentProvider provider,
            @Param("processed") Boolean processed,
            @Param("eventType") String eventType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"store"})
    @Query("""
            select e
            from WebhookEvent e
            where (:storeId is null or e.store.id = :storeId)
            and (:provider is null or e.provider = :provider)
            and (:processed is null or e.processed = :processed)
            and (:eventType is null or upper(e.eventType) = upper(cast(:eventType as string)))
            order by e.createdAt desc
            """)
    List<WebhookEvent> searchPlatform(
            @Param("storeId") UUID storeId,
            @Param("provider") PaymentProvider provider,
            @Param("processed") Boolean processed,
            @Param("eventType") String eventType
    );
}
