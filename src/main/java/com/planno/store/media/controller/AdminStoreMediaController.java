package com.planno.store.media.controller;

import com.planno.store.media.dto.MediaUploadResponse;
import com.planno.store.media.service.StoreMediaService;
import com.planno.store.store.service.CurrentStoreProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/store/current/assets")
public class AdminStoreMediaController {

    private final CurrentStoreProvider currentStoreProvider;
    private final StoreMediaService storeMediaService;

    public AdminStoreMediaController(CurrentStoreProvider currentStoreProvider, StoreMediaService storeMediaService) {
        this.currentStoreProvider = currentStoreProvider;
        this.storeMediaService = storeMediaService;
    }

    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MediaUploadResponse> uploadLogo(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(storeMediaService.uploadLogo(currentStoreProvider.getCurrentStore(), file));
    }

    @PostMapping("/banner")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MediaUploadResponse> uploadBanner(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(storeMediaService.uploadBanner(currentStoreProvider.getCurrentStore(), file));
    }
}
