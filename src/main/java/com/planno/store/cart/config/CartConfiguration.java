package com.planno.store.cart.config;

import com.planno.store.cart.service.CartTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CartTokenProperties.class)
public class CartConfiguration {
}

