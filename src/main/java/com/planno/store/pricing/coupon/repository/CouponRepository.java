package com.planno.store.pricing.coupon.repository;

import com.planno.store.pricing.coupon.domain.Coupon;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    Optional<Coupon> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Coupon> findByStoreIdAndCodeIgnoreCase(UUID storeId, String code);

    boolean existsByStoreIdAndCodeIgnoreCase(UUID storeId, String code);

    boolean existsByStoreIdAndCodeIgnoreCaseAndIdNot(UUID storeId, String code, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id and c.store.id = :storeId")
    Optional<Coupon> findWithLockByIdAndStoreId(@Param("id") UUID id, @Param("storeId") UUID storeId);
}
