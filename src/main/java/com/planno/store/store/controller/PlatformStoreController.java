package com.planno.store.store.controller;

import com.planno.store.store.dto.PlatformStoreRequest;
import com.planno.store.store.dto.StoreResponse;
import com.planno.store.store.service.PlatformStoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/stores")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformStoreController {

    private final PlatformStoreService platformStoreService;

    public PlatformStoreController(PlatformStoreService platformStoreService) {
        this.platformStoreService = platformStoreService;
    }

    @GetMapping
    public ResponseEntity<List<StoreResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(platformStoreService.list(status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(platformStoreService.get(id));
    }

    @PostMapping
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody PlatformStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformStoreService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoreResponse> update(@PathVariable UUID id, @Valid @RequestBody PlatformStoreRequest request) {
        return ResponseEntity.ok(platformStoreService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        platformStoreService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
