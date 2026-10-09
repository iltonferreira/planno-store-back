package com.planno.store.store.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProvisioningService {

    private final StoreRepository stores;
    private final StoreProvisioningRepository operations;
    private final ObjectMapper objectMapper;
    private final StoreProvisioningProperties properties;

    public StoreProvisioningService(StoreRepository stores, StoreProvisioningRepository operations,
            ObjectMapper objectMapper, StoreProvisioningProperties properties) {
        this.stores = stores;
        this.operations = operations;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public StoreProvisioningResponse provision(StoreProvisioningRequest request) {
        String key = request.idempotencyKey().trim();
        String requestHash = hash(request);
        var prior = operations.findByIdempotencyKey(key);
        if (prior.isPresent()) {
            StoreProvisioningOperation operation = prior.get();
            if (!MessageDigest.isEqual(operation.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ConflictException("Idempotency key was already used for a different store request");
            }
            return response(operation);
        }

        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        if (stores.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Store slug already exists");
        }
        Store store = stores.save(Store.create(request.name(), slug, request.contactEmail(), "BRL",
                "America/Sao_Paulo", "Empresa: " + request.companyName().trim(), null, null,
                null, null, null));
        StoreProvisioningOperation operation = operations.save(new StoreProvisioningOperation(key, requestHash,
                store.getId(), request.storeAdminEmail(), request.storeAdminName(), request.planCode()));
        return response(operation);
    }

    private StoreProvisioningResponse response(StoreProvisioningOperation operation) {
        String suffix = properties.domainSuffix() == null || properties.domainSuffix().isBlank()
                ? "planno.store" : properties.domainSuffix().trim().replaceAll("^\\.+|\\.+$", "");
        return new StoreProvisioningResponse(operation.getId(), operation.getStoreId(), operation.getStatus(),
                operation.getStoreId() == null ? null : stores.findById(operation.getStoreId()).map(s -> s.getSlug() + "." + suffix).orElse(null));
    }

    private String hash(StoreProvisioningRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not fingerprint store provisioning request", exception);
        }
    }
}
