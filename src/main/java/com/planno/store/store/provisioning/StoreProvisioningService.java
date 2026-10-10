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
        String suffix = properties.domainSuffix() == null || properties.domainSuffix().isBlank()
                ? "planno.online" : properties.domainSuffix().trim().replaceAll("^\\.+|\\.+$", "").toLowerCase(Locale.ROOT);
        String expectedDomain = slug + "." + suffix;
        String publicDomain = request.publicDomain() == null || request.publicDomain().isBlank()
                ? expectedDomain : request.publicDomain().trim().toLowerCase(Locale.ROOT);
        // The Hub owns the initial Planno storefront zone. Accept it even when an
        // older VPS environment still has the previous default suffix configured.
        if (!expectedDomain.equals(publicDomain) && !(slug + ".planno.online").equals(publicDomain)) {
            throw new ConflictException("The initial store domain must match its reserved Planno subdomain");
        }
        if (stores.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("Store slug already exists");
        }
        Store store = stores.save(Store.create(request.name(), slug, request.contactEmail(), "BRL",
                "America/Sao_Paulo", "Empresa: " + request.companyName().trim(), null, null,
                null, null, null));
        store.setPublicDomain(publicDomain);
        StoreProvisioningOperation operation = operations.save(new StoreProvisioningOperation(key, requestHash,
                store.getId(), request.storeAdminEmail(), request.storeAdminName(), request.planCode()));
        return response(operation);
    }

    @Transactional
    public void updatePublicDomain(UUID storeId, String domain) {
        Store store = stores.findById(storeId).orElseThrow(() -> new com.planno.store.core.exception.NotFoundException("Store not found"));
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        store.setPublicDomain(normalized);
    }

    private StoreProvisioningResponse response(StoreProvisioningOperation operation) {
        return new StoreProvisioningResponse(operation.getId(), operation.getStoreId(), operation.getStatus(),
                operation.getStoreId() == null ? null : stores.findById(operation.getStoreId()).map(Store::getPublicDomain).orElse(null));
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
