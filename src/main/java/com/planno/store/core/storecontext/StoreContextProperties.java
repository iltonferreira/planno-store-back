package com.planno.store.core.storecontext;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.store-context")
public record StoreContextProperties(
        String headerName
) {
}

