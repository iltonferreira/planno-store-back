package com.planno.store.cart.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cart")
public record CartTokenProperties(
        String tokenHeaderName
) {
}

