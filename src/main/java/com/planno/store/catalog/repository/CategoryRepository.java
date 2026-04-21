package com.planno.store.catalog.repository;

import com.planno.store.catalog.domain.Category;
import com.planno.store.catalog.domain.CategoryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByStoreIdOrderByNameAsc(UUID storeId);

    List<Category> findAllByStoreIdAndStatusOrderByNameAsc(UUID storeId, CategoryStatus status);

    Optional<Category> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<Category> findBySlugIgnoreCaseAndStoreId(String slug, UUID storeId);

    Optional<Category> findBySlugIgnoreCaseAndStoreIdAndStatus(String slug, UUID storeId, CategoryStatus status);

    boolean existsByStoreIdAndSlugIgnoreCase(UUID storeId, String slug);

    boolean existsByStoreIdAndSlugIgnoreCaseAndIdNot(UUID storeId, String slug, UUID id);
}
