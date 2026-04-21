package com.planno.store.cart.repository;

import com.planno.store.cart.domain.CartItem;
import com.planno.store.cart.domain.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @EntityGraph(attributePaths = {"product", "cart", "cart.store"})
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    @EntityGraph(attributePaths = {"product", "cart", "cart.store"})
    Optional<CartItem> findByIdAndCartStoreIdAndCartGuestTokenAndCartStatus(
            UUID id,
            UUID storeId,
            String guestToken,
            CartStatus status
    );
}

