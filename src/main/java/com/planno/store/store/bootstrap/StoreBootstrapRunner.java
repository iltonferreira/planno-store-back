package com.planno.store.store.bootstrap;

import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class StoreBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreBootstrapRunner.class);

    private final StoreBootstrapProperties properties;
    private final StoreRepository storeRepository;

    public StoreBootstrapRunner(StoreBootstrapProperties properties, StoreRepository storeRepository) {
        this.properties = properties;
        this.storeRepository = storeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }

        if (properties.name() == null || properties.name().isBlank()
                || properties.slug() == null || properties.slug().isBlank()
                || properties.contactEmail() == null || properties.contactEmail().isBlank()) {
            log.warn("Store bootstrap skipped because required store environment variables are missing.");
            return;
        }

        String normalizedSlug = properties.slug().trim();
        if (storeRepository.existsBySlugIgnoreCase(normalizedSlug)) {
            log.info("Store bootstrap skipped because store slug '{}' already exists.", normalizedSlug);
            return;
        }

        if (storeRepository.count() > 0) {
            log.info("Store bootstrap skipped because a store already exists and this phase supports a single active store bootstrap.");
            return;
        }

        storeRepository.save(Store.create(
                properties.name(),
                normalizedSlug,
                properties.contactEmail(),
                properties.currency(),
                properties.timezone(),
                properties.description(),
                properties.logoUrl(),
                properties.bannerUrl(),
                properties.primaryColor(),
                properties.secondaryColor(),
                properties.commissionRate()
        ));
        log.info("Store bootstrap completed for slug '{}'.", normalizedSlug);
    }
}
