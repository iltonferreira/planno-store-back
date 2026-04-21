package com.planno.store.pricing.promotion.repository;

import com.planno.store.pricing.promotion.domain.Promotion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

    List<Promotion> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    Optional<Promotion> findByIdAndStoreId(UUID id, UUID storeId);

    List<Promotion> findAllByStoreIdAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            UUID storeId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    );
}
