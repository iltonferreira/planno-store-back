package com.planno.store.shipping.repository;

import com.planno.store.shipping.domain.Address;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Optional<Address> findByOrderId(UUID orderId);
}
