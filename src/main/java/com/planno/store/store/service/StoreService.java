package com.planno.store.store.service;

import com.planno.store.store.domain.Store;
import com.planno.store.store.dto.StoreResponse;
import com.planno.store.store.dto.UpdateStoreRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final CurrentStoreProvider currentStoreProvider;

    public StoreService(CurrentStoreProvider currentStoreProvider) {
        this.currentStoreProvider = currentStoreProvider;
    }

    @Transactional(readOnly = true)
    public StoreResponse getCurrentStore() {
        return StoreResponse.from(currentStoreProvider.getCurrentStore());
    }

    @Transactional
    public StoreResponse updateCurrentStore(UpdateStoreRequest request) {
        Store store = currentStoreProvider.getCurrentStore();
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
                store.getCommissionRate()
        );
        return StoreResponse.from(store);
    }
}
