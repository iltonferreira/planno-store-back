package com.planno.store.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.order-reservation")
public record OrderReservationProperties(
        @DefaultValue("PT30M") Duration ttl,
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100") int batchSize,
        @DefaultValue("PT1M") Duration fixedDelay
) {
    public OrderReservationProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Order reservation TTL must be positive");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Order reservation batch size must be between 1 and 1000");
        }
        if (fixedDelay == null || fixedDelay.toMillis() < 1) {
            throw new IllegalArgumentException("Order reservation fixed delay must be positive");
        }
    }
}
