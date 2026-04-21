package com.planno.store.shipping.repository;

import com.planno.store.shipping.domain.ShippingMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, UUID> {

    List<ShippingMethod> findAllByStoreIdOrderByNameAsc(UUID storeId);

    List<ShippingMethod> findAllByStoreIdAndActiveTrueOrderByPriceAsc(UUID storeId);

    Optional<ShippingMethod> findByIdAndStoreId(UUID id, UUID storeId);
}

