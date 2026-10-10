package com.planno.store.store.provisioning;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.planno.store.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/stores")
public class StoreProvisioningController {

    private final StoreProvisioningService service;
    private final StoreProvisioningProperties properties;

    public StoreProvisioningController(StoreProvisioningService service, StoreProvisioningProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/provision")
    public ResponseEntity<StoreProvisioningResponse> provision(HttpServletRequest httpRequest,
            @Valid @RequestBody StoreProvisioningRequest request) {
        String configuredToken = properties.serviceToken();
        if (configuredToken == null || configuredToken.length() < 32) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Store provisioning service is not configured");
        }
        String suppliedToken = httpRequest.getHeader("X-Planno-Service-Token");
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.provision(request));
    }

    @PutMapping("/{storeId}/domain")
    public ResponseEntity<Void> updateDomain(HttpServletRequest httpRequest, @PathVariable java.util.UUID storeId,
            @Valid @RequestBody StoreDomainRequest request) {
        requireServiceToken(httpRequest);
        service.updatePublicDomain(storeId, request.domain());
        return ResponseEntity.noContent().build();
    }

    private void requireServiceToken(HttpServletRequest httpRequest) {
        String configuredToken = properties.serviceToken();
        if (configuredToken == null || configuredToken.length() < 32) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Store provisioning service is not configured");
        }
        String suppliedToken = httpRequest.getHeader("X-Planno-Service-Token");
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid service token");
        }
    }
}
