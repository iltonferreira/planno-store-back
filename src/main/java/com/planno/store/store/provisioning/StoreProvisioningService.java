package com.planno.store.store.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.Role;
import com.planno.store.identity.domain.User;
import com.planno.store.identity.domain.UserRoleAssignment;
import com.planno.store.identity.repository.RoleRepository;
import com.planno.store.identity.repository.UserRepository;
import com.planno.store.identity.repository.UserRoleAssignmentRepository;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProvisioningService {

    private final StoreRepository stores;
    private final StoreProvisioningRepository operations;
    private final ObjectMapper objectMapper;
    private final StoreProvisioningProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public StoreProvisioningService(StoreRepository stores, StoreProvisioningRepository operations,
            ObjectMapper objectMapper, StoreProvisioningProperties properties, UserRepository users,
            RoleRepository roles, UserRoleAssignmentRepository assignments, PasswordEncoder passwordEncoder) {
        this.stores = stores;
        this.operations = operations;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.passwordEncoder = passwordEncoder;
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
        if (users.findByEmailIgnoreCase(request.storeAdminEmail()).isPresent()) {
            throw new ConflictException("Store administrator already has an account; do not replace an existing password.");
        }
        String randomUnsharedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        User admin = users.save(User.create(request.storeAdminEmail(), passwordEncoder.encode(randomUnsharedPassword),
                firstName(request.storeAdminName()), lastName(request.storeAdminName())));
        admin.requirePasswordChange();
        var adminRole = roles.findByName(RoleName.ADMIN)
                .orElseGet(() -> roles.save(new Role(RoleName.ADMIN)));
        if (!assignments.existsByUserIdAndRoleIdAndStoreId(admin.getId(), adminRole.getId(), store.getId())) {
            assignments.save(new UserRoleAssignment(admin, adminRole, store));
        }
        StoreProvisioningOperation operation = operations.save(new StoreProvisioningOperation(key, requestHash,
                store.getId(), request.storeAdminEmail(), request.storeAdminName(), request.planCode()));
        return response(operation);
    }

    @Transactional
    public StoreAdminAccessResponse issueAdminAccess(UUID storeId) {
        StoreProvisioningOperation operation = operations.findByStoreId(storeId)
                .orElseThrow(() -> new com.planno.store.core.exception.NotFoundException("Store provisioning not found"));
        User admin = users.findByEmailIgnoreCase(operation.getStoreAdminEmail())
                .orElseThrow(() -> new IllegalStateException("Store administrator account was not created"));
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(24));
        admin.setTemporaryPassword(passwordEncoder.encode(temporaryPassword));
        return new StoreAdminAccessResponse(admin.getEmail(), temporaryPassword);
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

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static String firstName(String name) {
        String[] parts = name.trim().split("\\s+", 2);
        return parts[0];
    }

    private static String lastName(String name) {
        String[] parts = name.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "Administrador";
    }

    public record StoreAdminAccessResponse(String email, String temporaryPassword) { }
}
