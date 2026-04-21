package com.planno.store.media.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.media.config.SupabaseStorageProperties;
import com.planno.store.media.dto.MediaUploadResponse;
import com.planno.store.store.domain.Store;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoreMediaService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private final SupabaseStorageProperties properties;
    private final RestClient restClient;

    public StoreMediaService(SupabaseStorageProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public MediaUploadResponse uploadLogo(Store store, MultipartFile file) {
        MediaUploadResponse uploaded = upload(store, "logo", file);
        store.updateBranding(uploaded.publicUrl(), store.getBannerUrl());
        return uploaded;
    }

    @Transactional
    public MediaUploadResponse uploadBanner(Store store, MultipartFile file) {
        MediaUploadResponse uploaded = upload(store, "banner", file);
        store.updateBranding(store.getLogoUrl(), uploaded.publicUrl());
        return uploaded;
    }

    private MediaUploadResponse upload(Store store, String kind, MultipartFile file) {
        validateFile(file);
        String extension = resolveExtension(file.getContentType());
        String folder = StringUtils.hasText(properties.storeImageFolder()) ? properties.storeImageFolder().trim() : "stores";
        String path = folder + "/" + store.getSlug() + "/" + kind + "-" + UUID.randomUUID() + "." + extension;

        if (properties.mockEnabled()) {
            return new MediaUploadResponse(properties.bucket(), path, buildPublicUrl(path));
        }

        if (!StringUtils.hasText(properties.url()) || !StringUtils.hasText(properties.serviceRoleKey()) || !StringUtils.hasText(properties.bucket())) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Supabase Storage is not configured");
        }

        try {
            restClient.post()
                    .uri(properties.url() + "/storage/v1/object/" + properties.bucket() + "/" + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                    .header("apikey", properties.serviceRoleKey())
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
            return new MediaUploadResponse(properties.bucket(), path, buildPublicUrl(path));
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file");
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Supabase Storage upload failed");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported image content type");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Image file exceeds the 5MB limit");
        }
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported image content type");
        };
    }

    private String buildPublicUrl(String path) {
        String baseUrl = StringUtils.hasText(properties.publicBaseUrl()) ? properties.publicBaseUrl().trim() : properties.url();
        return baseUrl + "/storage/v1/object/public/" + properties.bucket() + "/" + path;
    }
}
