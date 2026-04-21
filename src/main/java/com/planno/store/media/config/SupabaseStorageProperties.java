package com.planno.store.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media.supabase")
public record SupabaseStorageProperties(
        boolean mockEnabled,
        String url,
        String serviceRoleKey,
        String publicBaseUrl,
        String bucket,
        String storeImageFolder
) {
}
