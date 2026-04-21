package com.planno.store.media.controller;

import com.planno.store.media.dto.MediaUploadResponse;
import com.planno.store.media.service.StoreMediaService;
import com.planno.store.store.service.PlatformStoreService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/platform/stores/{storeId}/assets")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformStoreMediaController {

    private final PlatformStoreService platformStoreService;
    private final StoreMediaService storeMediaService;

    public PlatformStoreMediaController(PlatformStoreService platformStoreService, StoreMediaService storeMediaService) {
        this.platformStoreService = platformStoreService;
        this.storeMediaService = storeMediaService;
    }

    @PostMapping("/logo")
    public ResponseEntity<MediaUploadResponse> uploadLogo(@PathVariable UUID storeId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(storeMediaService.uploadLogo(platformStoreService.getEntity(storeId), file));
    }

    @PostMapping("/banner")
    public ResponseEntity<MediaUploadResponse> uploadBanner(@PathVariable UUID storeId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(storeMediaService.uploadBanner(platformStoreService.getEntity(storeId), file));
    }
}
