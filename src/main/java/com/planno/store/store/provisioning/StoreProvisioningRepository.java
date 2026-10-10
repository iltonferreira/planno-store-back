package com.planno.store.store.provisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProvisioningRepository extends JpaRepository<StoreProvisioningOperation, UUID> {
    Optional<StoreProvisioningOperation> findByIdempotencyKey(String idempotencyKey);
    Optional<StoreProvisioningOperation> findByStoreId(UUID storeId);
}
