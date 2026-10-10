package com.planno.store.cart.repository;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    Optional<Cart> findByStoreIdAndGuestTokenAndStatus(UUID storeId, String guestToken, CartStatus status);

}
