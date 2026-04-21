package com.planno.store.store.bootstrap;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.store")
public record StoreBootstrapProperties(
        boolean enabled,
        String name,
        String slug,
        String contactEmail,
        String currency,
        String timezone,
        String description,
        String logoUrl,
        String bannerUrl,
        String primaryColor,
        String secondaryColor,
        BigDecimal commissionRate
) {
}
