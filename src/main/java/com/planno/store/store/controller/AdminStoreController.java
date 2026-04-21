package com.planno.store.store.controller;

import com.planno.store.store.dto.StoreResponse;
import com.planno.store.store.dto.UpdateStoreRequest;
import com.planno.store.store.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/store")
public class AdminStoreController {

    private final StoreService storeService;

    public AdminStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<StoreResponse> getCurrentStore() {
        return ResponseEntity.ok(storeService.getCurrentStore());
    }

    @PutMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> updateCurrentStore(@Valid @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(storeService.updateCurrentStore(request));
    }
}

