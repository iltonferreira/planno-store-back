package com.planno.store.cart.repository;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = {"store", "items", "items.product"})
    Optional<Cart> findByStoreIdAndGuestTokenAndStatus(UUID storeId, String guestToken, CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :cartId and c.store.id = :storeId and c.guestToken = :guestToken")
    Optional<Cart> findForCheckoutUpdate(@Param("cartId") UUID cartId, @Param("storeId") UUID storeId,
            @Param("guestToken") String guestToken);

}
