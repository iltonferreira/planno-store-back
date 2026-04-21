package com.planno.store.store.controller;

import com.planno.store.store.dto.StoreResponse;
import com.planno.store.store.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/store")
public class PublicStoreController {

    private final StoreService storeService;

    public PublicStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/current")
    public ResponseEntity<StoreResponse> getCurrentStore() {
        return ResponseEntity.ok(storeService.getCurrentStore());
    }
}

