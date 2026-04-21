package com.planno.store.catalog.repository;

import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = {"store", "category"})
    List<Product> findAllByStoreIdOrderByNameAsc(UUID storeId);

    @EntityGraph(attributePaths = {"store", "category"})
    List<Product> findAllByStoreIdAndStatusOrderByNameAsc(UUID storeId, ProductStatus status);

    @EntityGraph(attributePaths = {"store", "category"})
    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    @EntityGraph(attributePaths = {"store", "category"})
    Optional<Product> findBySlugIgnoreCaseAndStoreIdAndStatus(String slug, UUID storeId, ProductStatus status);

    boolean existsByStoreIdAndSlugIgnoreCase(UUID storeId, String slug);

    boolean existsByStoreIdAndSlugIgnoreCaseAndIdNot(UUID storeId, String slug, UUID id);

    boolean existsByStoreIdAndSkuIgnoreCase(UUID storeId, String sku);

    boolean existsByStoreIdAndSkuIgnoreCaseAndIdNot(UUID storeId, String sku, UUID id);
}

