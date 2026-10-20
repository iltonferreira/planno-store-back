package com.planno.store.media.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.media.config.SupabaseStorageProperties;
import com.planno.store.media.dto.MediaUploadResponse;
import com.planno.store.store.domain.Store;
import com.planno.store.store.repository.StoreRepository;
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

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private final SupabaseStorageProperties properties;
    private final StoreRepository storeRepository;
    private final RestClient restClient;

    public StoreMediaService(SupabaseStorageProperties properties, StoreRepository storeRepository, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public MediaUploadResponse uploadLogo(Store store, MultipartFile file) {
        MediaUploadResponse uploaded = upload(store, "logo", file);
        store.updateBranding(uploaded.publicUrl(), store.getBannerUrl());
        storeRepository.save(store);
        return uploaded;
    }

    @Transactional
    public MediaUploadResponse uploadBanner(Store store, MultipartFile file) {
        MediaUploadResponse uploaded = upload(store, "banner", file);
        store.updateBranding(store.getLogoUrl(), uploaded.publicUrl());
        storeRepository.save(store);
        return uploaded;
    }

    private MediaUploadResponse upload(Store store, String kind, MultipartFile file) {
        byte[] content = validateFile(file);
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
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
            return new MediaUploadResponse(properties.bucket(), path, buildPublicUrl(path));
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Supabase Storage upload failed");
        }
    }

    private byte[] validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported image content type");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Image file exceeds the 5MB limit");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > MAX_FILE_SIZE) throw new IOException("Invalid size");
            if ("image/webp".equals(file.getContentType())) {
                if (content.length < 20 || !ascii(content, 0, 4).equals("RIFF") || !ascii(content, 8, 4).equals("WEBP")
                        || !Set.of("VP8 ", "VP8L", "VP8X").contains(ascii(content, 12, 4))
                        || Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(content, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()) != content.length - 8L) {
                    throw new IOException("Invalid WebP");
                }
            } else {
                try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(content))) {
                    var readers = javax.imageio.ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IOException("Invalid image");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        String format = reader.getFormatName();
                        boolean matches = "image/png".equals(file.getContentType()) ? format.equalsIgnoreCase("png")
                                : format.equalsIgnoreCase("jpeg");
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (!matches || width <= 0 || height <= 0 || (long) width * height > 16_000_000 || reader.read(0) == null)
                            throw new IOException("Invalid image dimensions or format");
                    } finally { reader.dispose(); }
                }
            }
            return content;
        } catch (IOException | RuntimeException invalid) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid image content");
        }
    }

    private static String ascii(byte[] bytes, int start, int length) {
        return new String(bytes, start, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "Unsupported image content type");
        };
    }

    private String buildPublicUrl(String path) {
        String baseUrl = StringUtils.hasText(properties.publicBaseUrl()) ? properties.publicBaseUrl().trim() : properties.url();
        return baseUrl + "/storage/v1/object/public/" + properties.bucket() + "/" + path;
    }
}
