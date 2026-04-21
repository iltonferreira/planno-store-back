package com.planno.store.catalog.service;

import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.catalog.dto.ProductResponse;
import com.planno.store.catalog.repository.ProductRepository;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final CurrentStoreProvider currentStoreProvider;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService,
            CurrentStoreProvider currentStoreProvider
    ) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listAdmin() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return productRepository.findAllByStoreIdOrderByNameAsc(storeId)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listPublic() {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        return productRepository.findAllByStoreIdAndStatusOrderByNameAsc(storeId, ProductStatus.ACTIVE)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID productId) {
        return ProductResponse.from(getProductEntity(productId));
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublicBySlug(String slug) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        Product product = productRepository.findBySlugIgnoreCaseAndStoreIdAndStatus(slug, storeId, ProductStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        validateUniqueFields(storeId, request.slug(), request.sku(), null);

        Product product = Product.create(
                currentStoreProvider.getCurrentStore(),
                categoryService.getOptionalCategory(request.categoryId()),
                request.name(),
                request.slug(),
                request.sku(),
                request.description(),
                request.status(),
                request.basePrice(),
                request.mainImageUrl()
        );
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID productId, ProductRequest request) {
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        validateUniqueFields(storeId, request.slug(), request.sku(), productId);

        Product product = getProductEntity(productId);
        product.update(
                categoryService.getOptionalCategory(request.categoryId()),
                request.name(),
                request.slug(),
                request.sku(),
                request.description(),
                request.status(),
                request.basePrice(),
                request.mainImageUrl()
        );
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(UUID productId) {
        productRepository.delete(getProductEntity(productId));
    }

    @Transactional(readOnly = true)
    public Product getProductEntity(UUID productId) {
        return getProductEntity(currentStoreProvider.getCurrentStoreId(), productId);
    }

    @Transactional(readOnly = true)
    public Product getProductEntity(UUID storeId, UUID productId) {
        return productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void validateUniqueFields(UUID storeId, String slug, String sku, UUID productId) {
        boolean slugExists = productId == null
                ? productRepository.existsByStoreIdAndSlugIgnoreCase(storeId, slug)
                : productRepository.existsByStoreIdAndSlugIgnoreCaseAndIdNot(storeId, slug, productId);
        if (slugExists) {
            throw new ConflictException("Product slug already exists for the current store");
        }

        boolean skuExists = productId == null
                ? productRepository.existsByStoreIdAndSkuIgnoreCase(storeId, sku)
                : productRepository.existsByStoreIdAndSkuIgnoreCaseAndIdNot(storeId, sku, productId);
        if (skuExists) {
            throw new ConflictException("Product SKU already exists for the current store");
        }
    }
}
