package com.planno.store.media.dto;

public record MediaUploadResponse(
        String bucket,
        String path,
        String publicUrl
) {
}
