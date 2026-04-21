package com.planno.store.catalog.service;

import com.planno.store.catalog.domain.Category;
import com.planno.store.catalog.domain.CategoryStatus;
import com.planno.store.catalog.dto.CategoryRequest;
import com.planno.store.catalog.dto.CategoryResponse;
import com.planno.store.catalog.repository.CategoryRepository;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentStoreProvider currentStoreProvider;

    public CategoryService(CategoryRepository categoryRepository, CurrentStoreProvider currentStoreProvider) {
        this.categoryRepository = categoryRepository;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAdmin() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return categoryRepository.findAllByStoreIdOrderByNameAsc(storeId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listPublic() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return categoryRepository.findAllByStoreIdAndStatusOrderByNameAsc(storeId, CategoryStatus.ACTIVE)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID categoryId) {
        return CategoryResponse.from(getCategoryEntity(categoryId));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        validateSlugUniqueness(storeId, request.slug(), null);

        Category category = Category.create(
                currentStoreProvider.getCurrentStore(),
                request.name(),
                request.slug(),
                request.description(),
                request.status()
        );
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, CategoryRequest request) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        validateSlugUniqueness(storeId, request.slug(), categoryId);

        Category category = getCategoryEntity(categoryId);
        category.update(request.name(), request.slug(), request.description(), request.status());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(UUID categoryId) {
        categoryRepository.delete(getCategoryEntity(categoryId));
    }

    @Transactional(readOnly = true)
    public Category getOptionalCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return getCategoryEntity(categoryId);
    }

    private Category getCategoryEntity(UUID categoryId) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return categoryRepository.findByIdAndStoreId(categoryId, storeId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private void validateSlugUniqueness(UUID storeId, String slug, UUID categoryId) {
        boolean exists = categoryId == null
                ? categoryRepository.existsByStoreIdAndSlugIgnoreCase(storeId, slug)
                : categoryRepository.existsByStoreIdAndSlugIgnoreCaseAndIdNot(storeId, slug, categoryId);
        if (exists) {
            throw new ConflictException("Category slug already exists for the current store");
        }
    }
}

