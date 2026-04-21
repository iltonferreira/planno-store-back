package com.planno.store.store.service;

import com.planno.store.core.exception.ConflictException;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.store.domain.Store;
import com.planno.store.store.dto.PlatformStoreRequest;
import com.planno.store.store.dto.StoreResponse;
import com.planno.store.store.repository.StoreRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformStoreService {

    private final StoreRepository storeRepository;

    public PlatformStoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> list(String status, String search) {
        Store.StoreStatus storeStatus = parseStatus(status);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return storeRepository.search(storeStatus, normalizedSearch).stream()
                .map(StoreResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID id) {
        return StoreResponse.from(getEntity(id));
    }

    @Transactional
    public StoreResponse create(PlatformStoreRequest request) {
        validateUniqueSlug(request.slug(), null);
        Store store = storeRepository.save(Store.create(
                request.name(),
                request.slug(),
                request.contactEmail(),
                request.currency(),
                request.timezone(),
                request.description(),
                request.logoUrl(),
                request.bannerUrl(),
                request.primaryColor(),
                request.secondaryColor(),
                request.commissionRate()
        ));
        return StoreResponse.from(store);
    }

    @Transactional
    public StoreResponse update(UUID id, PlatformStoreRequest request) {
        Store store = getEntity(id);
        validateUniqueSlug(request.slug(), id);
        store.renameSlug(request.slug());
        store.update(
                request.name(),
                request.contactEmail(),
                request.currency(),
                request.timezone(),
                request.description(),
                request.logoUrl(),
                request.bannerUrl(),
                request.primaryColor(),
                request.secondaryColor(),
                request.commissionRate()
        );
        return StoreResponse.from(store);
    }

    @Transactional
    public void deactivate(UUID id) {
        getEntity(id).deactivate();
    }

    @Transactional(readOnly = true)
    public Store getEntity(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private void validateUniqueSlug(String slug, UUID currentId) {
        boolean exists = currentId == null
                ? storeRepository.existsBySlugIgnoreCase(slug)
                : storeRepository.existsBySlugIgnoreCaseAndIdNot(slug, currentId);
        if (exists) {
            throw new ConflictException("Store slug already exists");
        }
    }

    private Store.StoreStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Store.StoreStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid store status filter");
        }
    }
}
