package com.planno.store.store.service;

import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.storecontext.StoreContext;
import com.planno.store.core.storecontext.StoreContextHolder;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentStoreProvider {

    private final CurrentStoreResolver currentStoreResolver;
    private final StoreRepository storeRepository;

    public CurrentStoreProvider(CurrentStoreResolver currentStoreResolver, StoreRepository storeRepository) {
        this.currentStoreResolver = currentStoreResolver;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public Store getCurrentStore() {
        StoreContext context = getCurrentContext();
        return storeRepository.findById(context.storeId())
                .orElseThrow(() -> new NotFoundException("Current store not found"));
    }

    public UUID getCurrentStoreId() {
        return getCurrentContext().storeId();
    }

    public StoreContext getCurrentContext() {
        return StoreContextHolder.get()
                .orElseGet(() -> {
                    StoreContext resolved = currentStoreResolver.resolve();
                    StoreContextHolder.set(resolved);
                    return resolved;
                });
    }
}

